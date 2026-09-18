package dev.dada.doorsensor.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dada.doorsensor.config.MqttProperties;
import dev.dada.doorsensor.service.DoorService;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class MqttIngestService implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(MqttIngestService.class);

    private final MqttProperties props;
    private final DoorService doorService;
    private final ObjectMapper objectMapper;
    private MqttClient client;

    public MqttIngestService(MqttProperties props, DoorService doorService, ObjectMapper objectMapper) {
        this.props = props;
        this.doorService = doorService;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void connect() {
        try {
            client = new MqttClient(props.brokerUrl(),
                    props.clientId() + "-" + System.currentTimeMillis(),
                    new MemoryPersistence());
            client.setCallback(this);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(props.automaticReconnect());
            options.setCleanSession(props.cleanSession());
            options.setConnectionTimeout(props.connectionTimeoutSeconds());
            options.setKeepAliveInterval(props.keepAliveSeconds());
            if (props.username() != null && !props.username().isBlank()) {
                options.setUserName(props.username());
                options.setPassword(props.password().toCharArray());
            }

            client.connect(options);
            log.info("Da ket noi MQTT broker tai {}", props.brokerUrl());
        } catch (Exception e) {
            log.error("Khong ket noi duoc MQTT broker tai {}. REST API van hoat dong. Loi: {}",
                    props.brokerUrl(), e.getMessage());
        }
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        try {
            client.subscribe(props.stateTopic(), props.qos());
            client.subscribe(props.statusTopic(), props.qos());
            log.info("Da subscribe: {} va {} (reconnect={})",
                    props.stateTopic(), props.statusTopic(), reconnect);
        } catch (Exception e) {
            log.error("Subscribe that bai: {}", e.getMessage());
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        log.debug("MQTT {} -> {}", topic, payload);

        try {
            String deviceId = extractDeviceId(topic);
            if (deviceId == null) {
                log.warn("Bo qua message tu topic khong dung dinh dang: {}", topic);
                return;
            }

            if (topic.endsWith("/status")) {
                handleStatus(deviceId, payload);
            } else if (topic.endsWith("/state")) {
                DevicePayload parsed = objectMapper.readValue(payload, DevicePayload.class);
                doorService.handleStateMessage(deviceId, parsed);
            }
        } catch (Exception e) {
            log.warn("Khong xu ly duoc message tu {}: {}", topic, e.getMessage());
        }
    }

    private void handleStatus(String deviceId, String payload) {
        String v = payload.trim().toLowerCase();
        if (v.startsWith("online")) {
            doorService.markOnline(deviceId);
        } else if (v.startsWith("offline")) {
            doorService.markOfflineFromLwt(deviceId);
        } else {
            log.warn("Gia tri status la: {}", payload);
        }
    }

    private String extractDeviceId(String topic) {
        String[] parts = topic.split("/");
        return parts.length >= 4 ? parts[2] : null;
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("Mat ket noi MQTT: {}. Paho se tu thu lai.",
                cause == null ? "khong ro" : cause.getMessage());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    @PreDestroy
    public void disconnect() {
        try {
            if (client != null && client.isConnected()) {
                client.disconnect();
                client.close();
                log.info("Da dong ket noi MQTT");
            }
        } catch (Exception e) {
            log.warn("Loi khi dong ket noi MQTT: {}", e.getMessage());
        }
    }
}
