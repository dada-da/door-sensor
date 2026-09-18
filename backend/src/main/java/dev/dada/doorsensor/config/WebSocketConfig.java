package dev.dada.doorsensor.config;

import dev.dada.doorsensor.web.RealtimeHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final RealtimeHandler realtimeHandler;
    private final CorsProperties corsProperties;

    public WebSocketConfig(RealtimeHandler realtimeHandler, CorsProperties corsProperties) {
        this.realtimeHandler = realtimeHandler;
        this.corsProperties = corsProperties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(realtimeHandler, "/ws/live")
                .setAllowedOrigins(corsProperties.originArray());
    }
}
