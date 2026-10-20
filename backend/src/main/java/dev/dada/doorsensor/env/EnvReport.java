package dev.dada.doorsensor.env;

import dev.dada.doorsensor.config.NotifyProperties;
import dev.dada.doorsensor.env.EnvAlertEvaluator.Direction;
import dev.dada.doorsensor.notify.TelegramClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Noi dung tra loi lenh Telegram /nhietdo: so do moi nhat, thap nhat va cao nhat
 * 24 gio, nguong dang dat va canh bao dang mo.
 */
@Component
public class EnvReport {

    /** Node gui moi 60 giay. Qua 5 phut khong co tin thi so tren man hinh da cu. */
    static final long STALE_SECONDS = 300;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private final SensorReadingRepository readings;
    private final EnvAlertRuleRepository rules;
    private final EnvAlertService alerts;
    private final Clock clock;
    private final ZoneId zone;

    public EnvReport(SensorReadingRepository readings, EnvAlertRuleRepository rules, EnvAlertService alerts,
                     Clock clock, NotifyProperties notify) {
        this.readings = readings;
        this.rules = rules;
        this.alerts = alerts;
        this.clock = clock;
        this.zone = notify.quietHours() != null && notify.quietHours().zone() != null
                ? notify.quietHours().zoneId()
                : ZoneId.of("Asia/Ho_Chi_Minh");
    }

    /** Mot dong cua mot thiet bi, tach khoi JPA de kiem thu. */
    record Line(String deviceId, Instant receivedAt, Double temp, Double rh,
                Double tempMin, Double tempMax, Double rhMin, Double rhMax) {
    }

    public String telegramText() {
        Instant now = clock.instant();
        Map<String, SensorReadingRepository.Range> ranges = new HashMap<>();
        for (SensorReadingRepository.Range r : readings.rangeSince(now.minus(Duration.ofHours(24)))) {
            ranges.put(r.getDeviceId(), r);
        }
        List<Line> lines = readings.findLatestPerDevice().stream()
                .map(r -> {
                    SensorReadingRepository.Range g = ranges.get(r.getDeviceId());
                    return new Line(r.getDeviceId(), r.getReceivedAt(), r.getTemperatureC(), r.getHumidityPct(),
                            g == null ? null : g.getTempMin(), g == null ? null : g.getTempMax(),
                            g == null ? null : g.getRhMin(), g == null ? null : g.getRhMax());
                })
                .toList();
        return format(lines, rules.current(), alerts.active(), now, zone);
    }

    /** Mot dong ngan cho /trangthai, null neu chua co so do. */
    public String statusLine() {
        List<SensorReading> latest = readings.findLatestPerDevice();
        if (latest.isEmpty()) {
            return null;
        }
        SensorReading r = latest.get(0);
        long age = Duration.between(r.getReceivedAt(), clock.instant()).getSeconds();
        String line = "Nhiệt độ " + EnvAlertService.num(r.getTemperatureC()) + " °C, độ ẩm "
                + EnvAlertService.num(r.getHumidityPct()) + " %"
                + (age > STALE_SECONDS ? " (đã cũ " + EnvAlertService.minutesText(age / 60) + ")" : "");
        int open = alerts.active().size();
        return open == 0 ? line : line + "\n⚠️ <b>" + open + " cảnh báo môi trường đang mở</b> (/nhietdo)";
    }

    static String format(List<Line> lines, EnvAlertRule rule, List<EnvAlertEvaluator.Active> active,
                         Instant now, ZoneId zone) {
        if (lines.isEmpty()) {
            return "<b>Chưa có số đo nhiệt độ, độ ẩm nào.</b>\nKiểm tra node còi và cảm biến AHT20.";
        }
        StringBuilder sb = new StringBuilder();
        for (Line l : lines) {
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            long age = Duration.between(l.receivedAt(), now).getSeconds();
            sb.append("🌡️ <b>").append(EnvAlertService.num(l.temp())).append(" °C</b>   ")
              .append("💧 <b>").append(EnvAlertService.num(l.rh())).append(" %</b>\n");
            sb.append(TelegramClient.escape(l.deviceId())).append(", đo lúc ")
              .append(ZonedDateTime.ofInstant(l.receivedAt(), zone).format(CLOCK));
            if (age > STALE_SECONDS) {
                sb.append(" ⚠️ <b>đã cũ ").append(EnvAlertService.minutesText(age / 60))
                  .append("</b>, node có thể mất kết nối");
            }
            if (l.tempMin() != null || l.rhMin() != null) {
                sb.append("\n24 giờ qua: ")
                  .append(EnvAlertService.num(l.tempMin())).append("–").append(EnvAlertService.num(l.tempMax()))
                  .append(" °C, ")
                  .append(EnvAlertService.num(l.rhMin())).append("–").append(EnvAlertService.num(l.rhMax()))
                  .append(" %");
            }
        }

        sb.append("\n\n");
        if (!rule.isEnabled()) {
            sb.append("Cảnh báo nhiệt độ, độ ẩm: <b>đang tắt</b>.");
        } else {
            sb.append("Ngưỡng cảnh báo: ")
              .append(range("nhiệt độ", rule.getTempLow(), rule.getTempHigh(), " °C")).append("; ")
              .append(range("độ ẩm", rule.getRhLow(), rule.getRhHigh(), " %"))
              .append(" (vượt liên tục ").append(rule.getHoldMinutes()).append(" phút mới báo).");
        }

        for (EnvAlertEvaluator.Active a : active) {
            sb.append("\n⚠️ <b>").append(EnvAlertService.name(a.metric()))
              .append(a.direction() == Direction.HIGH ? " cao" : " thấp")
              .append("</b> từ ").append(ZonedDateTime.ofInstant(a.since(), zone).format(CLOCK))
              .append(", ").append(a.direction() == Direction.HIGH ? "cao nhất " : "thấp nhất ")
              .append(EnvAlertService.num(a.peak())).append(EnvAlertService.unit(a.metric()));
        }
        return sb.toString();
    }

    private static String range(String label, Double low, Double high, String unit) {
        if (low == null && high == null) {
            return label + " không đặt";
        }
        if (low == null) {
            return label + " trên " + EnvAlertService.num(high) + unit;
        }
        if (high == null) {
            return label + " dưới " + EnvAlertService.num(low) + unit;
        }
        return label + " ngoài " + EnvAlertService.num(low) + "–" + EnvAlertService.num(high) + unit;
    }
}
