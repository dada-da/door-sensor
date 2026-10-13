package dev.dada.doorsensor.env;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dada.doorsensor.config.EnvProperties;
import dev.dada.doorsensor.config.MqttProperties;
import dev.dada.doorsensor.mqtt.MqttClients;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

/**
 * Nhan so do moi truong tu home/env/+/telemetry va ghi vao sensor_reading.
 *
 * <p>Co y tach khoi {@code MqttIngestService}: client MQTT rieng, khong goi vao
 * DoorService, khong dung bang device. Nho vay co the khang dinh duong nhan su
 * kien cua tu T0 khong bi sua mot dong nao, va loi o phan nay (payload hong, CSDL
 * cham) khong anh huong toi duong nhan cu.
 *
 * <p>Thiet bi moi tu xuat hien: ma thiet bi lay tu chu de, khong can dang ky
 * truoc.
 */
@Service
public class EnvIngestService implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(EnvIngestService.class);

    private final MqttProperties mqtt;
    private final EnvProperties props;
    private final SensorReadingRepository repo;
    private final ObjectMapper objectMapper;
    private final TaskScheduler scheduler;
    private volatile MqttClient client;

    public EnvIngestService(MqttProperties mqtt, EnvProperties props, SensorReadingRepository repo,
                            ObjectMapper objectMapper, TaskScheduler scheduler) {
        this.mqtt = mqtt;
        this.props = props;
        this.repo = repo;
        this.objectMapper = objectMapper;
        this.scheduler = scheduler;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void connect() {
        if (!props.enabled()) {
            log.info("Thu du lieu moi truong dang tat (app.env.enabled=false)");
            return;
        }
        try {
            client = MqttClients.create(mqtt, "env");
            client.setCallback(this);
        } catch (Exception e) {
            // Chi loi khi URL broker sai. Khong nem tiep: thieu du lieu moi truong
            // khong duoc lam ung dung dung lai.
            log.error("[env] Khong tao duoc MQTT client: {}", e.getMessage());
            return;
        }
        MqttClients.connectWithRetry(client, MqttClients.options(mqtt), scheduler, "env", log);
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        try {
            client.subscribe(props.telemetryTopic(), props.qos());
            log.info("[env] Da subscribe {} (reconnect={})", props.telemetryTopic(), reconnect);
        } catch (Exception e) {
            log.error("[env] Subscribe that bai: {}", e.getMessage());
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        handle(topic, new String(message.getPayload(), StandardCharsets.UTF_8), Instant.now());
    }

    /** Tach rieng khoi callback de kiem thu ma khong can broker. */
    void handle(String topic, String payload, Instant receivedAt) {
        try {
            String deviceId = MqttClients.deviceIdFromTopic(topic);
            if (deviceId == null) {
                log.warn("[env] Bo qua chu de sai dinh dang: {}", topic);
                return;
            }
            EnvPayload p = objectMapper.readValue(payload, EnvPayload.class);
            String reason = EnvReadingRules.rejectReason(p.temperatureC(), p.humidityPct());
            if (reason != null) {
                log.warn("[env] Bo qua so do cua {}: {}. Payload: {}", deviceId, reason, payload);
                return;
            }
            Instant measuredAt = EnvReadingRules.measuredAt(p.ts(), receivedAt);
            repo.save(new SensorReading(deviceId, measuredAt, receivedAt,
                    p.temperatureC(), p.humidityPct()));
            log.debug("[env] {} {} C {} %", deviceId, p.temperatureC(), p.humidityPct());
        } catch (Exception e) {
            log.warn("[env] Khong xu ly duoc tin tu {}: {}", topic, e.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("[env] Mat ket noi MQTT: {}. Paho se tu thu lai.",
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
            }
        } catch (Exception e) {
            log.warn("[env] Loi khi dong ket noi MQTT: {}", e.getMessage());
        }
    }
}
