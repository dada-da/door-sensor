package dev.dada.doorsensor.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dada.doorsensor.web.dto.RealtimeMessage;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class RealtimeHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(RealtimeHandler.class);

    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private final ObjectMapper objectMapper;

    public RealtimeHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.debug("Dashboard ket noi: {} (tong {})", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.debug("Dashboard ngat: {} (con {})", session.getId(), sessions.size());
    }

    public void broadcast(RealtimeMessage message) {
        if (sessions.isEmpty()) {
            return;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (IOException e) {
            log.warn("Khong serialize duoc goi tin realtime: {}", e.getMessage());
            return;
        }

        TextMessage frame = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    synchronized (session) {
                        session.sendMessage(frame);
                    }
                } else {
                    sessions.remove(session);
                }
            } catch (Exception e) {
                log.debug("Gui that bai toi {}, go session. Loi: {}", session.getId(), e.getMessage());
                sessions.remove(session);
            }
        }
    }

    public int activeSessions() {
        return sessions.size();
    }
}
