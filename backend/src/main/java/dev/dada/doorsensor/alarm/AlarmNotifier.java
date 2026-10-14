package dev.dada.doorsensor.alarm;

import dev.dada.doorsensor.config.ClockConfig;
import dev.dada.doorsensor.notify.TelegramClient;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Tin Telegram cua phan bao dong. Gui qua TelegramClient (bat dong bo, ton trong
 * app.notify.enabled), khong qua NotificationService vi cac tin nay khong ap
 * dung luat gom tin mo cua: mot lan bao dong la mot tin, khong duoc gom.
 */
@Component
public class AlarmNotifier {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DAY_CLOCK = DateTimeFormatter.ofPattern("HH:mm dd/MM");

    private final TelegramClient telegram;

    public AlarmNotifier(TelegramClient telegram) {
        this.telegram = telegram;
    }

    public void intrusion(String doorLabel, Instant occurredAt, int sirenSeconds,
                          boolean commandOk, boolean sirenOnline) {
        StringBuilder sb = new StringBuilder()
                .append("<b>BÁO ĐỘNG: ").append(TelegramClient.escape(doorLabel))
                .append(" mở lúc ").append(time(occurredAt)).append("</b>\n")
                .append("Đang trong giờ bảo vệ.");
        if (commandOk) {
            sb.append(" Đã bật còi ").append(sirenSeconds).append(" giây.");
            if (!sirenOnline) {
                sb.append("\n<i>Còi đang báo mất liên lạc, có thể không kêu.</i>");
            }
        } else {
            sb.append("\n<b>Không gửi được lệnh tới còi</b>, còi gần như chắc chắn không kêu.");
        }
        if (commandOk) {
            // Bao dong nham (nguoi nha ve khuya): tat ngay tu dien thoai, khong can mo giao dien.
            telegram.send(sb.toString(), List.of(List.<String[]>of(
                    new String[]{"🔕 Tắt còi", TelegramAlarmControl.CALLBACK_STOP})));
        } else {
            telegram.send(sb.toString());
        }
    }

    public void sirenOffline(Instant lastSeen) {
        telegram.send("""
                <b>Còi báo động mất liên lạc</b>
                Hệ thống đang ở chế độ bảo vệ nhưng còi sẽ không kêu được.

                Nhận tin gần nhất: %s"""
                .formatted(lastSeen == null ? "chưa từng kể từ khi máy chủ khởi động" : dayTime(lastSeen)));
    }

    public void sirenBack() {
        telegram.send("<b>Còi báo động đã kết nối lại</b>");
    }

    public void openAtArmStart(List<String> doorLabels) {
        String doors = doorLabels.stream()
                .map(TelegramClient::escape)
                .collect(Collectors.joining(", "));
        telegram.send("""
                <b>Bắt đầu giờ bảo vệ, cửa đang mở</b>
                %s

                <i>Còi không kêu vì cửa đã mở sẵn. Nếu cửa đóng rồi mở lại trong giờ bảo vệ thì còi sẽ kêu.</i>"""
                .formatted(doors));
    }

    public void selfTestFailed(String reason) {
        telegram.send("""
                <b>Tự thử còi thất bại</b>
                %s

                <i>Đường lệnh tới còi có thể đã hỏng dù còi vẫn báo trực tuyến.</i>"""
                .formatted(TelegramClient.escape(reason)));
    }

    private static String time(Instant at) {
        return ZonedDateTime.ofInstant(at, ClockConfig.ZONE).format(CLOCK);
    }

    private static String dayTime(Instant at) {
        return ZonedDateTime.ofInstant(at, ClockConfig.ZONE).format(DAY_CLOCK);
    }
}
