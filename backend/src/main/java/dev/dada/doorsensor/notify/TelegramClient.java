package dev.dada.doorsensor.notify;

import dev.dada.doorsensor.config.NotifyProperties;
import java.time.Duration;
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
        if (!props.enabled()) {
            return;
        }
        if (!props.telegram().configured()) {
            log.warn("Bo qua thong bao: chua dat bot-token va chat-id trong cau hinh.");
            return;
        }

        try {
            http.post()
                .uri(API + "/bot" + props.telegram().botToken() + "/sendMessage")
                .body(Map.of(
                        "chat_id", props.telegram().chatId(),
                        "text", html,
                        "parse_mode", "HTML",
                        "disable_web_page_preview", true))
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
