package dev.dada.doorsensor.alarm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dada.doorsensor.config.NotifyProperties;
import dev.dada.doorsensor.env.EnvReport;
import dev.dada.doorsensor.notify.TelegramClient;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Dieu khien tu Telegram: nut "Tat coi" trong tin BAO DONG, lenh /tatcoi, /trangthai,
 * /nhietdo (nhiet do, do am hien tai va nguong canh bao) va /help.
 *
 * <p><b>Vi sao long polling chu khong phai webhook.</b> Webhook can Telegram goi
 * duoc vao Pi tu Internet: phai mo cong, co ten mien, co chung chi TLS. Pi nam sau
 * NAT cua mang gia dinh. Long polling thi Pi tu goi ra getUpdates va giu ket noi
 * toi 25 giay, co tin la tra ve ngay, nen do tre gan nhu bang webhook ma khong
 * phai mo gi ra ngoai.
 *
 * <p><b>Ai duoc ra lenh.</b> Chi dung chat-id da cau hinh. Bot Telegram la cong
 * khai, ai biet ten bot cung nhan tin duoc; khong kiem tra chat thi ai cung tat
 * duoc coi. Tin tu cho khac bi bo qua, khong tra loi (tra loi la xac nhan bot
 * dang dieu khien thu gi do).
 *
 * <p><b>Lenh cu.</b> Telegram giu tin chua lay toi 24 gio. Sau khi Pi khoi dong
 * lai, mot lenh /tatcoi gui tu hom qua khong duoc tat coi cua hom nay, nen lenh
 * chu cu hon {@link #MAX_COMMAND_AGE} bi bo qua. Nut bam thi khong gioi han tuoi,
 * vi nguoi dung co the bam nut tren tin bao dong vai phut sau moi thay.
 */
@Component
public class TelegramAlarmControl {

    private static final Logger log = LoggerFactory.getLogger(TelegramAlarmControl.class);
    private static final String API = "https://api.telegram.org";

    public static final String CALLBACK_STOP = "siren_stop";
    static final Duration MAX_COMMAND_AGE = Duration.ofMinutes(2);
    private static final int POLL_TIMEOUT_S = 25;

    private final NotifyProperties notify;
    private final TelegramClient telegram;
    private final Actions alarm;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final boolean enabled;
    private final RestClient http;

    /** Nhung viec bot lam duoc. Tach ra de kiem thu ma khong dung ca AlarmService. */
    interface Actions {
        AlarmService.CommandResult stopFromTelegram();

        AlarmStatusDto status();

        /** Noi dung /nhietdo. */
        default String envReport() {
            return "Chưa có số đo nhiệt độ, độ ẩm.";
        }

        /** Mot dong moi truong them vao /trangthai, null neu chua co. */
        default String envStatusLine() {
            return null;
        }
    }

    /** Danh sach lenh hien trong nut menu "/" cua Telegram (setMyCommands). */
    static final String[][] COMMANDS = {
            {"trangthai", "Trạng thái báo động, còi, nhiệt độ"},
            {"nhietdo", "Nhiệt độ, độ ẩm hiện tại và ngưỡng cảnh báo"},
            {"tatcoi", "Tắt còi đang kêu"},
            {"help", "Danh sách lệnh"},
    };

    private volatile boolean running;
    private long offset;

    @org.springframework.beans.factory.annotation.Autowired
    public TelegramAlarmControl(NotifyProperties notify, TelegramClient telegram, AlarmService alarm,
                                EnvReport env, ObjectMapper objectMapper, Clock clock, RestClient.Builder builder,
                                @Value("${app.alarm.telegram-control:true}") boolean enabled) {
        this(notify, telegram, new Actions() {
            @Override public AlarmService.CommandResult stopFromTelegram() { return alarm.stopFromTelegram(); }

            @Override public AlarmStatusDto status() { return alarm.status(); }

            @Override public String envReport() { return env.telegramText(); }

            @Override public String envStatusLine() { return env.statusLine(); }
        }, objectMapper, clock, builder, enabled);
    }

    TelegramAlarmControl(NotifyProperties notify, TelegramClient telegram, Actions alarm,
                         ObjectMapper objectMapper, Clock clock, RestClient.Builder builder,
                         boolean enabled) {
        this.notify = notify;
        this.telegram = telegram;
        this.alarm = alarm;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.enabled = enabled;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        // Lon hon thoi gian giu ket noi cua long polling, neu khong moi lan cho la mot lan loi.
        factory.setReadTimeout(Duration.ofSeconds(POLL_TIMEOUT_S + 15));
        this.http = builder.requestFactory(factory).build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!enabled || !telegram.ready()) {
            log.info("[telegram] Khong nhan lenh tu Telegram (tat, hoac chua cau hinh bot)");
            return;
        }
        running = true;
        telegram.setCommands(COMMANDS);
        Thread t = new Thread(this::loop, "telegram-poll");
        t.setDaemon(true);
        t.start();
        log.info("[telegram] Dang nhan lenh /tatcoi, /trangthai, /nhietdo va nut Tat coi");
    }

    @PreDestroy
    public void stop() {
        running = false;
    }

    private void loop() {
        long backoffMs = 5000;
        while (running) {
            try {
                String body = http.get()
                        .uri(API + "/bot" + notify.telegram().botToken()
                                + "/getUpdates?timeout=" + POLL_TIMEOUT_S + "&offset=" + offset
                                + "&allowed_updates=%5B%22message%22%2C%22callback_query%22%5D")
                        .retrieve()
                        .body(String.class);
                JsonNode root = objectMapper.readTree(body);
                for (JsonNode update : root.path("result")) {
                    offset = Math.max(offset, update.path("update_id").asLong() + 1);
                    handleUpdate(update);
                }
                backoffMs = 5000;
            } catch (Exception e) {
                // 409 Conflict: co tien trinh khac cung getUpdates bang bot nay (hoac da dat webhook).
                log.warn("[telegram] getUpdates loi: {}. Thu lai sau {} giay", e.getMessage(), backoffMs / 1000);
                sleep(backoffMs);
                backoffMs = Math.min(backoffMs * 2, 60_000);
            }
        }
    }

    /** Ket qua xu ly mot update, de kiem thu. */
    enum Outcome { IGNORED_FOREIGN_CHAT, IGNORED_STALE, IGNORED_OTHER, STOPPED, STOP_FAILED, STATUS, ENV, HELP }

    Outcome handleUpdate(JsonNode update) {
        JsonNode callback = update.path("callback_query");
        if (!callback.isMissingNode()) {
            String chatId = callback.path("message").path("chat").path("id").asText();
            if (!isOurChat(chatId)) {
                log.warn("[telegram] Bo qua nut bam tu chat la {}", chatId);
                return Outcome.IGNORED_FOREIGN_CHAT;
            }
            if (!CALLBACK_STOP.equals(callback.path("data").asText())) {
                return Outcome.IGNORED_OTHER;
            }
            AlarmService.CommandResult r = alarm.stopFromTelegram();
            telegram.answerCallback(callback.path("id").asText(), stopText(r, true));
            if (r == AlarmService.CommandResult.OK) {
                telegram.reply("<b>Đã tắt còi</b> từ Telegram.");
            }
            return r == AlarmService.CommandResult.OK ? Outcome.STOPPED : Outcome.STOP_FAILED;
        }

        JsonNode message = update.path("message");
        if (message.isMissingNode()) {
            return Outcome.IGNORED_OTHER;
        }
        String chatId = message.path("chat").path("id").asText();
        if (!isOurChat(chatId)) {
            log.warn("[telegram] Bo qua tin tu chat la {}", chatId);
            return Outcome.IGNORED_FOREIGN_CHAT;
        }
        Instant sentAt = Instant.ofEpochSecond(message.path("date").asLong());
        if (sentAt.isBefore(clock.instant().minus(MAX_COMMAND_AGE))) {
            log.info("[telegram] Bo qua lenh cu luc {}", sentAt);
            return Outcome.IGNORED_STALE;
        }

        // "/tatcoi@ten_bot" trong nhom: bo phan sau dau @.
        String text = message.path("text").asText("").trim();
        String cmd = text.split("\\s+")[0].split("@")[0].toLowerCase();
        switch (cmd) {
            case "/tatcoi", "/stop" -> {
                AlarmService.CommandResult r = alarm.stopFromTelegram();
                telegram.reply(stopText(r, false));
                return r == AlarmService.CommandResult.OK ? Outcome.STOPPED : Outcome.STOP_FAILED;
            }
            case "/trangthai", "/status" -> {
                String env = safe(alarm::envStatusLine);
                telegram.reply(statusText(alarm.status()) + (env == null ? "" : "\n" + env));
                return Outcome.STATUS;
            }
            case "/nhietdo", "/env", "/moitruong" -> {
                String env = safe(alarm::envReport);
                telegram.reply(env == null ? "<b>Không đọc được số đo.</b> Thử lại sau." : env);
                return Outcome.ENV;
            }
            case "/help", "/start", "/lenh" -> {
                telegram.reply(helpText());
                return Outcome.HELP;
            }
            default -> {
                return Outcome.IGNORED_OTHER;
            }
        }
    }

    /** Loi CSDL khi doc moi truong khong duoc lam hong cau tra loi /trangthai. */
    private static String safe(java.util.function.Supplier<String> s) {
        try {
            return s.get();
        } catch (Exception e) {
            log.warn("[telegram] Khong doc duoc so do moi truong: {}", e.getMessage());
            return null;
        }
    }

    static String helpText() {
        StringBuilder sb = new StringBuilder("<b>Các lệnh</b>");
        for (String[] c : COMMANDS) {
            sb.append("\n/").append(c[0]).append(" – ").append(c[1]);
        }
        return sb.toString();
    }

    private boolean isOurChat(String chatId) {
        return chatId != null && !chatId.isBlank() && chatId.equals(notify.telegram().chatId().trim());
    }

    static String stopText(AlarmService.CommandResult r, boolean shortForm) {
        return switch (r) {
            case OK -> shortForm ? "Đã tắt còi" : "<b>Đã tắt còi.</b>";
            case SEND_FAILED -> shortForm
                    ? "Không gửi được lệnh, còi sẽ tự tắt khi hết giờ"
                    : "<b>Không gửi được lệnh tới còi.</b> Còi vẫn tự tắt khi hết thời lượng.";
            case DISABLED -> "Báo động đang tắt trong cấu hình";
            case BUSY -> "Còi đang bận";
        };
    }

    static String statusText(AlarmStatusDto s) {
        String level = switch (s.level()) {
            case "ARMED" -> "Đang bảo vệ";
            case "ARMED_SIREN_OFFLINE" -> "⚠️ Đang bảo vệ nhưng còi MẤT LIÊN LẠC";
            case "DISARMED" -> "Không bảo vệ";
            default -> "Báo động tắt trong cấu hình";
        };
        String siren = switch (s.sirenLink()) {
            case "ONLINE" -> "trực tuyến";
            case "OFFLINE" -> "mất liên lạc";
            default -> "chưa rõ";
        };
        String off = s.disabledDoors() == null || s.disabledDoors().isEmpty()
                ? ""
                : "\nĐang tắt báo động: " + TelegramClient.escape(String.join(", ", s.disabledDoors()));
        return "<b>%s</b>\nCòi: %s%s%s".formatted(level, siren, s.sirenActive() ? ", <b>đang kêu</b>" : "", off);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
