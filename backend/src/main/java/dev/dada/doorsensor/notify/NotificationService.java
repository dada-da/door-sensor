package dev.dada.doorsensor.notify;

import dev.dada.doorsensor.config.NotifyProperties;
import dev.dada.doorsensor.domain.Device;
import dev.dada.doorsensor.domain.DoorEvent;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final NotifyProperties props;
    private final TelegramClient telegram;
    private final OffHoursLookup offHours;

    private final Map<String, DoorThrottle> doorThrottles = new ConcurrentHashMap<>();
    private final Map<String, BatteryAlert> batteryAlerts = new ConcurrentHashMap<>();

    private final Map<String, PendingOffline> pendingOffline = new ConcurrentHashMap<>();
    private final Set<String> alertedOffline = ConcurrentHashMap.newKeySet();

    private record PendingOffline(Instant since, String label, String source, Instant lastSeen) {}

    private static final class DoorThrottle {
        volatile Instant lastSent = Instant.EPOCH;
        final AtomicInteger suppressed = new AtomicInteger();
        volatile Instant firstSuppressed;
        volatile String label = "";
    }

    private static final class BatteryAlert {
        volatile boolean alerted;
        volatile Instant alertedAt = Instant.EPOCH;
    }

    public NotificationService(NotifyProperties props, TelegramClient telegram, OffHoursLookup offHours) {
        this.props = props;
        this.telegram = telegram;
        this.offHours = offHours;
    }


    public void onDoorOpened(Device device, DoorEvent event) {
        if (!props.enabled() || !props.doorOpen().enabled()) {
            return;
        }

        // Khung gio rieng cua cua nay (alarm_door_rule), khong con quiet-hours chung.
        boolean quiet = offHours.isOffHours(device.getId(), event.getOccurredAt());
        if (props.doorOpen().onlyDuringQuietHours() && !quiet) {
            return;
        }

        DoorThrottle throttle = doorThrottles.computeIfAbsent(device.getId(), k -> new DoorThrottle());
        throttle.label = device.getLabel();

        Instant now = Instant.now();
        Duration since = Duration.between(throttle.lastSent, now);

        if (since.getSeconds() < props.doorOpen().minIntervalSeconds()) {
            if (throttle.suppressed.getAndIncrement() == 0) {
                throttle.firstSuppressed = now;
            }
            log.debug("Gom tin bao mo cua cua {} (da gom {} lan)",
                    device.getId(), throttle.suppressed.get());
            return;
        }

        throttle.lastSent = now;
        telegram.send(openMessage(device, event, quiet));
    }

    @Scheduled(fixedDelay = 30000)
    public void flushSuppressed() {
        if (!props.enabled() || !props.doorOpen().enabled()) {
            return;
        }

        Instant now = Instant.now();
        doorThrottles.forEach((deviceId, throttle) -> {
            if (throttle.suppressed.get() == 0) {
                return;
            }
            if (Duration.between(throttle.lastSent, now).getSeconds()
                    < props.doorOpen().minIntervalSeconds()) {
                return;
            }

            int count = throttle.suppressed.getAndSet(0);
            if (count == 0) {
                return;
            }
            throttle.lastSent = now;

            long spanSeconds = throttle.firstSuppressed == null
                    ? 0
                    : Duration.between(throttle.firstSuppressed, now).getSeconds();

            telegram.send("""
                    <b>%s</b>
                    Mở thêm %d lần trong %s vừa qua.

                    <i>Mở liên tục bất thường có thể do nam châm lệch hoặc cửa bị gió đập.</i>"""
                    .formatted(TelegramClient.escape(throttle.label), count, humanDuration(spanSeconds)));
        });
    }

    private String openMessage(Device device, DoorEvent event, boolean quiet) {
        String time = ZonedDateTime.ofInstant(event.getOccurredAt(), props.quietHours().zoneId())
                .format(CLOCK);

        String header = quiet
                ? "<b>%s mở lúc %s</b>\nNgoài khung giờ thường lệ."
                : "<b>%s mở lúc %s</b>";

        return header.formatted(TelegramClient.escape(device.getLabel()), time);
    }

    public void onDeviceOffline(Device device, String source) {
        if (!props.enabled() || !props.offline().enabled()) {
            return;
        }
        pendingOffline.putIfAbsent(device.getId(), new PendingOffline(
                Instant.now(), device.getLabel(), source, device.getLastSeen()));
        log.debug("{} mat lien lac, cho {} giay truoc khi bao",
                device.getId(), props.offline().graceSeconds());
    }

    @Scheduled(fixedDelay = 15000)
    public void flushPendingOffline() {
        if (!props.enabled() || !props.offline().enabled()) {
            return;
        }
        Instant now = Instant.now();

        pendingOffline.forEach((deviceId, pending) -> {
            if (Duration.between(pending.since(), now).getSeconds() < props.offline().graceSeconds()) {
                return;
            }
            pendingOffline.remove(deviceId);
            alertedOffline.add(deviceId);

            String reason = switch (pending.source()) {
                case "LWT" -> "Broker báo mất kết nối.";
                case "WATCHDOG" -> "Quá lâu không nhận được tin. Thiết bị có thể đã treo hoặc hết pin.";
                default -> "";
            };

            telegram.send("""
                    <b>%s mất liên lạc</b>
                    %s

                    Nhận tin gần nhất: %s"""
                    .formatted(
                            TelegramClient.escape(pending.label()),
                            reason,
                            pending.lastSeen() == null
                                    ? "chưa từng"
                                    : ZonedDateTime.ofInstant(pending.lastSeen(), props.quietHours().zoneId())
                                            .format(CLOCK)));
        });
    }

    public void onDeviceOnline(Device device) {
        if (!props.enabled() || !props.offline().enabled()) {
            return;
        }

        if (pendingOffline.remove(device.getId()) != null) {
            log.debug("{} noi lai trong khoang an toan, khong bao", device.getId());
            return;
        }

        if (alertedOffline.remove(device.getId())) {
            telegram.send("<b>%s đã kết nối lại</b>"
                    .formatted(TelegramClient.escape(device.getLabel())));
        }
    }

    // ------------------------------------------------------------- pin yếu

    public void onBatteryReading(Device device, Integer batteryMv) {
        if (!props.enabled() || !props.battery().enabled() || batteryMv == null) {
            return;
        }

        BatteryAlert state = batteryAlerts.computeIfAbsent(device.getId(), k -> new BatteryAlert());

        if (batteryMv >= props.battery().rearmAboveMv()) {
            if (state.alerted) {
                log.debug("{} đã sạc lại, nạp lại cảnh báo pin", device.getId());
            }
            state.alerted = false;
            return;
        }

        if (batteryMv >= props.battery().thresholdMv()) {
            return;
        }

        Instant now = Instant.now();
        boolean cooledDown = Duration.between(state.alertedAt, now).toHours()
                >= props.battery().cooldownHours();
        if (state.alerted && !cooledDown) {
            return;
        }

        state.alerted = true;
        state.alertedAt = now;

        telegram.send("""
                <b>%s sắp hết pin</b>
                Điện áp %.2f V, dưới ngưỡng %.2f V.

                <i>Pin Li-ion xả sâu dưới 3,0 V sẽ hỏng vĩnh viễn. Nên sạc trong vài ngày tới.</i>"""
                .formatted(
                        TelegramClient.escape(device.getLabel()),
                        batteryMv / 1000.0,
                        props.battery().thresholdMv() / 1000.0));
    }

    private static String humanDuration(long seconds) {
        if (seconds < 60) return seconds + " giây";
        long m = seconds / 60;
        if (m < 60) return m + " phút";
        return (m / 60) + " giờ " + (m % 60) + " phút";
    }
}
