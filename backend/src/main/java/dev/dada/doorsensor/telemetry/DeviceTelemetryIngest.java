package dev.dada.doorsensor.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dada.doorsensor.config.MqttProperties;
import dev.dada.doorsensor.config.TelemetryProperties;
import dev.dada.doorsensor.mqtt.MqttClients;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

/**
 * Ghi moi tin home/door/+/state vao device_telemetry: dien ap pin, thoi gian thuc,
 * RSSI cua tung lan thuc.
 *
 * <p>Cung khuon voi EnvIngestService: client MQTT rieng, khong goi DoorService, nen
 * duong nhan su kien cua tu T0 khong doi. Broker gui mot ban cua moi tin cho tung
 * client da dang ky, nen hai ben doc cung mot tin ma khong anh huong nhau.
 */
@Service
public class DeviceTelemetryIngest implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(DeviceTelemetryIngest.class);

    static final String INSERT = """
            INSERT INTO device_telemetry (device_id, received_at, device_ts, state, wake,
                                          battery_mv, batt_adc_mv, rssi, boot_count,
                                          work_ms, wifi_ms, mqtt_ms, rtt_ms)
            VALUES (:deviceId, :receivedAt, :deviceTs, :state, :wake,
                    :batteryMv, :battAdcMv, :rssi, :bootCount,
                    :workMs, :wifiMs, :mqttMs, :rttMs)
            """;

    private static final Instant CLOCK_FLOOR = Instant.parse("2020-01-01T00:00:00Z");

    private final MqttProperties mqtt;
    private final TelemetryProperties props;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final TaskScheduler scheduler;
    private volatile MqttClient client;

    public DeviceTelemetryIngest(MqttProperties mqtt, TelemetryProperties props, NamedParameterJdbcTemplate jdbc,
                                 ObjectMapper objectMapper, TaskScheduler scheduler) {
        this.mqtt = mqtt;
        this.props = props;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.scheduler = scheduler;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void connect() {
        if (!props.enabled()) {
            log.info("Ghi nhat ky thiet bi dang tat (app.telemetry.enabled=false)");
            return;
        }
        try {
            client = MqttClients.create(mqtt, "telemetry");
            client.setCallback(this);
        } catch (Exception e) {
            log.error("[telemetry] Khong tao duoc MQTT client: {}", e.getMessage());
            return;
        }
        MqttClients.connectWithRetry(client, MqttClients.options(mqtt), scheduler, "telemetry", log);
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        try {
            client.subscribe(props.stateTopic(), props.qos());
            log.info("[telemetry] Da subscribe {} (reconnect={})", props.stateTopic(), reconnect);
        } catch (Exception e) {
            log.error("[telemetry] Subscribe that bai: {}", e.getMessage());
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
                return;
            }
            TelemetryPayload p = objectMapper.readValue(payload, TelemetryPayload.class);
            jdbc.update(INSERT, params(deviceId, p, receivedAt));
        } catch (Exception e) {
            log.warn("[telemetry] Khong ghi duoc tin tu {}: {}", topic, e.getMessage());
        }
    }

    static MapSqlParameterSource params(String deviceId, TelemetryPayload p, Instant receivedAt) {
        return new MapSqlParameterSource()
                .addValue("deviceId", deviceId)
                .addValue("receivedAt", Timestamp.from(receivedAt))
                .addValue("deviceTs", deviceTs(p.ts(), receivedAt))
                .addValue("state", clip(p.state(), 8))
                .addValue("wake", clip(p.wake(), 16))
                .addValue("batteryMv", p.batteryMv())
                .addValue("battAdcMv", p.battAdcMv())
                .addValue("rssi", p.rssi())
                .addValue("bootCount", p.bootCount())
                .addValue("workMs", p.workMs())
                .addValue("wifiMs", p.wifiMs())
                .addValue("mqttMs", p.mqttMs())
                .addValue("rttMs", p.rttMs());
    }

    /** Dong ho thiet bi chua dong bo NTP bao nam 1970: ghi NULL thay vi mot moc sai. */
    static Timestamp deviceTs(Long ts, Instant receivedAt) {
        if (ts == null) return null;
        Instant t = Instant.ofEpochSecond(ts);
        if (t.isBefore(CLOCK_FLOOR) || t.isAfter(receivedAt.plus(Duration.ofDays(1)))) return null;
        return Timestamp.from(t);
    }

    private static String clip(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("[telemetry] Mat ket noi MQTT: {}. Paho se tu thu lai.",
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
            log.warn("[telemetry] Loi khi dong ket noi MQTT: {}", e.getMessage());
        }
    }
}
