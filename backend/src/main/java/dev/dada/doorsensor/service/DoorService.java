package dev.dada.doorsensor.service;

import dev.dada.doorsensor.domain.ConnectivityEvent;
import dev.dada.doorsensor.domain.Device;
import dev.dada.doorsensor.domain.DoorEvent;
import dev.dada.doorsensor.domain.DoorState;
import dev.dada.doorsensor.mqtt.DevicePayload;
import dev.dada.doorsensor.notify.NotificationService;
import dev.dada.doorsensor.repo.ConnectivityEventRepository;
import dev.dada.doorsensor.repo.DeviceRepository;
import dev.dada.doorsensor.repo.DoorEventRepository;
import dev.dada.doorsensor.web.RealtimeHandler;
import dev.dada.doorsensor.web.dto.DeviceStatusDto;
import dev.dada.doorsensor.web.dto.EventDto;
import dev.dada.doorsensor.web.dto.RealtimeMessage;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import org.springframework.scheduling.TaskScheduler;

@Service
public class DoorService {

    private static final Duration OFFLINE_NOTIFY_GRACE = Duration.ofSeconds(120);

    private final TaskScheduler scheduler;
    private final Map<String, ScheduledFuture<?>> pendingOfflineNotify = new ConcurrentHashMap<>();
    private final Set<String> offlineNotified = ConcurrentHashMap.newKeySet();

    private static final Logger log = LoggerFactory.getLogger(DoorService.class);

    private static final Instant CLOCK_FLOOR = Instant.parse("2020-01-01T00:00:00Z");
    private static final Duration CLOCK_CEILING_SKEW = Duration.ofDays(1);

    private static final Duration DEDUPE_WINDOW = Duration.ofSeconds(2);

    private final DeviceRepository deviceRepo;
    private final DoorEventRepository eventRepo;
    private final ConnectivityEventRepository connRepo;
    private final RealtimeHandler realtime;
    private final NotificationService notifier;

    public DoorService(DeviceRepository deviceRepo,
                       DoorEventRepository eventRepo,
                       ConnectivityEventRepository connRepo,
                       RealtimeHandler realtime,
                       NotificationService notifier,
                       TaskScheduler scheduler) {
        this.deviceRepo = deviceRepo;
        this.eventRepo = eventRepo;
        this.connRepo = connRepo;
        this.realtime = realtime;
        this.notifier = notifier;
        this.scheduler = scheduler;
    }

    @Transactional
    public void handleStateMessage(String deviceId, DevicePayload payload) {
        DoorState state = DoorState.parse(payload.state());
        if (state == null) {
            log.warn("Bo qua message tu {}: khong doc duoc trang thai '{}'", deviceId, payload.state());
            return;
        }

        Instant receivedAt = Instant.now();
        Instant occurredAt = resolveOccurredAt(payload.ts(), receivedAt, deviceId);

        Device device = deviceRepo.findById(deviceId)
                .orElseGet(() -> deviceRepo.save(new Device(deviceId, defaultLabel(deviceId, payload))));

        boolean isTransition = device.getLastState() == null || device.getLastState() != state;

        if (!isTransition) {
            applyTelemetry(device, payload, receivedAt);
            deviceRepo.save(device);
            log.debug("{} nhip song: van {}", deviceId, state);

            broadcastAfterCommit(RealtimeMessage.device(DeviceStatusDto.from(device)));
            afterCommit(() -> notifier.onBatteryReading(device, payload.batteryMv()));
            return;
        }

        Instant from = occurredAt.minus(DEDUPE_WINDOW);
        Instant to = occurredAt.plus(DEDUPE_WINDOW);
        if (eventRepo.existsDuplicate(deviceId, state.name(), from, to)) {
            log.debug("Bo qua ban trung lap: {} {} luc {}", deviceId, state, occurredAt);
            return;
        }

        DoorEvent event = new DoorEvent(deviceId, state, occurredAt, receivedAt);
        event.setBatteryMv(payload.batteryMv());
        event.setRssi(payload.rssi());
        event.setBootCount(payload.bootCount());

        if (device.getLastStateAt() != null && device.getLastState() != null
                && device.getLastState() != state) {
            long ms = Duration.between(device.getLastStateAt(), occurredAt).toMillis();
            if (ms >= 0) {
                event.setDurationMs(ms);
            }
        }

        eventRepo.save(event);

        device.setLastState(state);
        device.setLastStateAt(occurredAt);
        applyTelemetry(device, payload, receivedAt);
        deviceRepo.save(device);

        log.info("{} -> {} (tre {} ms)", deviceId, state, event.getLagMs());

        broadcastAfterCommit(RealtimeMessage.door(
                DeviceStatusDto.from(device), EventDto.from(event)));

        if (state == DoorState.OPEN) {
            afterCommit(() -> notifier.onDoorOpened(device, event));
        }
        afterCommit(() -> notifier.onBatteryReading(device, payload.batteryMv()));
    }

    private void applyTelemetry(Device device, DevicePayload payload, Instant receivedAt) {
        device.setLastSeen(receivedAt);
        device.setOnline(true);
        if (payload.batteryMv() != null) device.setLastBatteryMv(payload.batteryMv());
        if (payload.rssi() != null) device.setLastRssi(payload.rssi());
        if (payload.bootCount() != null) device.setBootCount(payload.bootCount());
        if (payload.label() != null && !payload.label().isBlank()) device.setLabel(payload.label());
    }

    @Transactional
    public void markOnline(String deviceId) {
        Device device = deviceRepo.findById(deviceId)
                .orElseGet(() -> deviceRepo.save(new Device(deviceId, deviceId)));
        if (device.isOnline()) {
            device.setLastSeen(Instant.now());
            deviceRepo.save(device);
            return;
        }
        ScheduledFuture<?> pending = pendingOfflineNotify.remove(deviceId);
        if (pending != null) pending.cancel(false);
        boolean wasNotified = offlineNotified.remove(deviceId);

        device.setOnline(true);
        device.setLastSeen(Instant.now());
        deviceRepo.save(device);
        connRepo.save(new ConnectivityEvent(deviceId, true, ConnectivityEvent.Source.REPORTED));
        log.info("{} da len mang", deviceId);
        broadcastAfterCommit(RealtimeMessage.device(DeviceStatusDto.from(device)));
        if (wasNotified) afterCommit(() -> notifier.onDeviceOnline(device));
    }

    @Transactional
    public void markOfflineFromLwt(String deviceId) {
        markOffline(deviceId, ConnectivityEvent.Source.LWT);
    }

    @Transactional
    public void markOfflineFromWatchdog(String deviceId) {
        markOffline(deviceId, ConnectivityEvent.Source.WATCHDOG);
    }

    private void markOffline(String deviceId, ConnectivityEvent.Source source) {
        deviceRepo.findById(deviceId).ifPresent(device -> {
            if (!device.isOnline()) {
                return;
            }
            device.setOnline(false);
            deviceRepo.save(device);
            connRepo.save(new ConnectivityEvent(deviceId, false, source));
            log.warn("{} mat lien lac (phat hien boi {})", deviceId, source);
            broadcastAfterCommit(RealtimeMessage.device(DeviceStatusDto.from(device)));
            if (source == ConnectivityEvent.Source.LWT) {
                afterCommit(() -> scheduleOfflineNotify(device, source.name()));
            } else {
                // watchdog da cho du nguong, bao ngay
                offlineNotified.add(deviceId);
                afterCommit(() -> notifier.onDeviceOffline(device, source.name()));
            }
        });
    }

    private Instant resolveOccurredAt(Long deviceTs, Instant receivedAt, String deviceId) {
        if (deviceTs == null) {
            return receivedAt;
        }
        Instant candidate = Instant.ofEpochSecond(deviceTs);
        if (candidate.isBefore(CLOCK_FLOOR) || candidate.isAfter(receivedAt.plus(CLOCK_CEILING_SKEW))) {
            log.warn("Dong ho cua {} bao {}, nam ngoai khoang hop le. Dung thoi diem nhan thay the.",
                    deviceId, candidate);
            return receivedAt;
        }
        return candidate;
    }

    private void broadcastAfterCommit(RealtimeMessage message) {
        afterCommit(() -> realtime.broadcast(message));
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private String defaultLabel(String deviceId, DevicePayload payload) {
        return (payload.label() != null && !payload.label().isBlank()) ? payload.label() : deviceId;
    }

    private void scheduleOfflineNotify(Device device, String source) {
        String id = device.getId();
        ScheduledFuture<?> f = scheduler.schedule(() -> {
            pendingOfflineNotify.remove(id);
            boolean stillOffline = deviceRepo.findById(id).map(d -> !d.isOnline()).orElse(false);
            if (stillOffline) {
                offlineNotified.add(id);
                notifier.onDeviceOffline(device, source);
            }
        }, Instant.now().plus(OFFLINE_NOTIFY_GRACE));
        ScheduledFuture<?> old = pendingOfflineNotify.put(id, f);
        if (old != null) old.cancel(false);
    }
}
