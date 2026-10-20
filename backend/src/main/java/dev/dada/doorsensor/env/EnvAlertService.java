package dev.dada.doorsensor.env;

import dev.dada.doorsensor.config.NotifyProperties;
import dev.dada.doorsensor.env.EnvAlertEvaluator.Direction;
import dev.dada.doorsensor.env.EnvAlertEvaluator.Metric;
import dev.dada.doorsensor.env.EnvAlertEvaluator.Transition;
import dev.dada.doorsensor.notify.TelegramClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Canh bao nhiet do, do am qua Telegram.
 *
 * <p>Nghe {@link EnvReadingSaved} (moi 60 giay mot lan tu node coi), xet tung dai
 * luong bang {@link EnvAlertEvaluator}, ghi nhat ky env_alert_event va gui tin.
 * Nhat ky van ghi khi Telegram tat (app.notify.enabled=false): giao dien va bao
 * cao van thay lich su vuot nguong.
 *
 * <p>Nguong doc lai tu CSDL moi lan co so do (mot truy van khoa chinh moi phut),
 * nen sua tren giao dien la co hieu luc ngay, khong can bo nho dem.
 */
@Service
public class EnvAlertService {

    private static final Logger log = LoggerFactory.getLogger(EnvAlertService.class);
    private static final Locale VI = Locale.forLanguageTag("vi-VN");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY_CLOCK = DateTimeFormatter.ofPattern("HH:mm dd/MM");

    private final EnvAlertRuleRepository rules;
    private final EnvAlertEventRepository events;
    private final TelegramClient telegram;
    private final ZoneId zone;
    private final Clock clock;
    private final EnvAlertEvaluator evaluator = new EnvAlertEvaluator();

    public EnvAlertService(EnvAlertRuleRepository rules, EnvAlertEventRepository events,
                           TelegramClient telegram, NotifyProperties notify, Clock clock) {
        this.rules = rules;
        this.events = events;
        this.telegram = telegram;
        this.zone = notify.quietHours() != null && notify.quietHours().zone() != null
                ? notify.quietHours().zoneId()
                : ZoneId.of("Asia/Ho_Chi_Minh");
        this.clock = clock;
    }

    /** Canh bao con mo luc tat may thi nap lai, de khong gui tin "vuot nguong" lan hai. */
    @EventListener(ApplicationReadyEvent.class)
    public void restore() {
        try {
            int n = 0;
            for (EnvAlertEvent e : events.findLatestPerDeviceAndMetric()) {
                if (e.getKind() == EnvAlertEvent.Kind.RAISED) {
                    evaluator.restore(e.getDeviceId(), e.getMetric(), e.getDirection(),
                            e.getOccurredAt(), e.getValue(), e.getThreshold());
                    n++;
                }
            }
            if (n > 0) {
                log.info("[env-alert] Khoi phuc {} canh bao dang mo", n);
            }
        } catch (Exception e) {
            log.warn("[env-alert] Khong doc duoc nhat ky canh bao: {}", e.getMessage());
        }
    }

    @EventListener
    public void onReading(EnvReadingSaved r) {
        try {
            EnvAlertRule rule = rules.current();
            if (!rule.isEnabled()) {
                return;
            }
            Duration hold = Duration.ofMinutes(Math.max(0, rule.getHoldMinutes()));
            // Gio may chu chu khong phai gio thiet bi: node coi khong bat buoc co NTP.
            Instant now = clock.instant();
            check(r.deviceId(), Metric.TEMP, r.temperatureC(), rule, hold, now);
            check(r.deviceId(), Metric.RH, r.humidityPct(), rule, hold, now);
        } catch (Exception e) {
            log.warn("[env-alert] Loi khi xet nguong cho {}: {}", r.deviceId(), e.getMessage());
        }
    }

    private void check(String deviceId, Metric m, Double value, EnvAlertRule rule, Duration hold, Instant now) {
        Optional<Transition> t = evaluator.evaluate(deviceId, m, value, rule.limits(m), hold, now);
        if (t.isEmpty()) {
            return;
        }
        Transition x = t.get();
        events.save(new EnvAlertEvent(x));
        log.info("[env-alert] {} {} {} {} gia tri {} nguong {}", deviceId, m, x.direction(),
                x.raised() ? "VUOT" : "HET", x.value(), x.threshold());
        telegram.send(message(x, zone));
    }

    /** Doi nguong tren giao dien. Tat thi quen het trang thai, khong bao "het" hang loat. */
    public void onRuleChanged(EnvAlertRule rule) {
        if (!rule.isEnabled()) {
            evaluator.reset();
        }
    }

    public List<EnvAlertEvaluator.Active> active() {
        return evaluator.active();
    }

    // ------------------------------------------------------------------ noi dung tin

    static String message(Transition t, ZoneId zone) {
        String what = name(t.metric());
        String unit = unit(t.metric());
        String since = ZonedDateTime.ofInstant(t.since(), zone).format(CLOCK);
        if (t.raised()) {
            String dir = t.direction() == Direction.HIGH ? "cao" : "thấp";
            String cmp = t.direction() == Direction.HIGH ? "trên" : "dưới";
            return """
                    %s <b>%s %s: %s%s</b>
                    Cảm biến %s, %s ngưỡng %s%s liên tục từ %s.%s"""
                    .formatted(icon(t.metric()), what, dir, num(t.value()), unit,
                            TelegramClient.escape(t.deviceId()), cmp, num(t.threshold()), unit, since,
                            hint(t.metric(), t.direction()));
        }
        String peakWord = t.direction() == Direction.HIGH ? "Cao nhất" : "Thấp nhất";
        long minutes = Math.max(1, Duration.between(t.since(), t.at()).toMinutes());
        return """
                ✅ <b>%s đã về bình thường: %s%s</b>
                %s %s%s, kéo dài %s (từ %s)."""
                .formatted(what, num(t.value()), unit, peakWord, num(t.peak()), unit,
                        minutesText(minutes), ZonedDateTime.ofInstant(t.since(), zone).format(DAY_CLOCK));
    }

    static String name(Metric m) {
        return m == Metric.TEMP ? "Nhiệt độ" : "Độ ẩm";
    }

    static String unit(Metric m) {
        return m == Metric.TEMP ? " °C" : " %";
    }

    static String icon(Metric m) {
        return m == Metric.TEMP ? "🌡️" : "💧";
    }

    /** Mot chu so thap phan, dau phay kieu Viet Nam. */
    static String num(Double v) {
        return v == null ? "--" : String.format(VI, "%.1f", v);
    }

    static String minutesText(long minutes) {
        if (minutes < 60) {
            return minutes + " phút";
        }
        long h = minutes / 60;
        long m = minutes % 60;
        return m == 0 ? h + " giờ" : h + " giờ " + m + " phút";
    }

    private static String hint(Metric m, Direction d) {
        String text = switch (m) {
            case TEMP -> d == Direction.HIGH
                    ? "Phòng nóng bất thường. Nếu không ai ở nhà, nhờ người kiểm tra thiết bị đang chạy."
                    : "Phòng lạnh bất thường. Kiểm tra cửa sổ.";
            case RH -> d == Direction.HIGH
                    ? "Ẩm kéo dài dễ gây mốc và rỉ mạch. Đóng cửa sổ, bật hút ẩm hoặc điều hòa chế độ khô."
                    : "Không khí khô bất thường.";
        };
        return "\n\n<i>" + text + "</i>";
    }
}
