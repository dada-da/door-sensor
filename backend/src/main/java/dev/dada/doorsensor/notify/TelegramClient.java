package dev.dada.doorsensor.notify;

import dev.dada.doorsensor.config.NotifyProperties;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TelegramClient {

    private static final Logger log = LoggerFactory.getLogger(TelegramClient.class);
    private static final String API = "https://api.telegram.org";

    private final NotifyProperties props;
    private final RestClient http;

    public TelegramClient(NotifyProperties props, RestClient.Builder builder) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = builder.requestFactory(factory).build();
    }

    @Async("notifyExecutor")
    public void send(String html) {
        doSend(html, null);
    }

    /**
     * Gui kem nut bam (inline keyboard). {@code buttons} la danh sach hang, moi hang
     * la danh sach cap [chu hien thi, callback_data].
     */
    @Async("notifyExecutor")
    public void send(String html, List<List<String[]>> buttons) {
        doSend(html, buttons);
    }

    /** Gui ngay tren luong goi. Dung cho tra loi lenh, khi da o luong rieng. */
    public void reply(String html) {
        doSend(html, null);
    }

    /** Tat vong xoay tren nut vua bam, hien mot dong ngan tren man hinh dien thoai. */
    public void answerCallback(String callbackId, String text) {
        if (!ready()) {
            return;
        }
        try {
            http.post()
                .uri(API + "/bot" + props.telegram().botToken() + "/answerCallbackQuery")
                .body(Map.of("callback_query_id", callbackId, "text", text))
                .retrieve()
                .toBodilessEntity();
        } catch (Exception e) {
            log.warn("answerCallbackQuery that bai: {}", e.getMessage());
        }
    }

    public boolean ready() {
        return props.enabled() && props.telegram().configured();
    }

    private void doSend(String html, List<List<String[]>> buttons) {
        if (!props.enabled()) {
            return;
        }
        if (!props.telegram().configured()) {
            log.warn("Bo qua thong bao: chua dat bot-token va chat-id trong cau hinh.");
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", props.telegram().chatId());
        body.put("text", html);
        body.put("parse_mode", "HTML");
        body.put("disable_web_page_preview", true);
        if (buttons != null && !buttons.isEmpty()) {
            List<List<Map<String, String>>> rows = buttons.stream()
                    .map(row -> row.stream()
                            .map(b -> Map.of("text", b[0], "callback_data", b[1]))
                            .toList())
                    .toList();
            body.put("reply_markup", Map.of("inline_keyboard", rows));
        }

        try {
            http.post()
                .uri(API + "/bot" + props.telegram().botToken() + "/sendMessage")
                .body(body)
                .retrieve()
                .toBodilessEntity();
            log.debug("Da gui thong bao Telegram");
        } catch (Exception e) {
            log.warn("Gui Telegram that bai: {}", e.getMessage());
        }
    }

    public static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;");
    }
}
