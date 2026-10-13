package dev.dada.doorsensor.alarm;

import dev.dada.doorsensor.config.AlarmProperties;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Tang MQTT cua bao dong. Khong chua logic nghiep vu: nhan tin thi phat su kien
 * Spring, duoc goi thi publish.
 *
 * <p>Client rieng, tach khoi MqttIngestService, cung ly do voi EnvIngestService.
 *
 * <p>Chu de theo bang 3.2 cua bao cao:
 * <pre>
 * home/alarm/{id}/cmd      may chu gui xuong, QoS 1, khong giu lai
 * home/alarm/{id}/state    node gui len, giu lai
 * home/alarm/{id}/status   di chuc, giu lai
 * home/alarm/armed         may chu gui xuong, QoS 1, giu lai
 * </pre>
 */
@Component
public class AlarmMqttGateway implements MqttCallbackExtended, SirenLink {

    private static final Logger log = LoggerFactory.getLogger(AlarmMqttGateway.class);

    static final String STATUS_SUB = "home/alarm/+/status";
    static final String STATE_SUB = "home/alarm/+/state";
    static final String ARMED_TOPIC = "home/alarm/armed";

    private final MqttProperties mqtt;
    private final AlarmProperties props;
    private final ApplicationEventPublisher events;
    private final TaskScheduler scheduler;
    private volatile MqttClient client;

    public AlarmMqttGateway(MqttProperties mqtt, AlarmProperties props,
                            ApplicationEventPublisher events, TaskScheduler scheduler) {
        this.mqtt = mqtt;
        this.props = props;
        this.events = events;
        this.scheduler = scheduler;
    }

    static String cmdTopic(String sirenId) {
        return "home/alarm/" + sirenId + "/cmd";
    }

    @EventListener(ApplicationReadyEvent.class)
    public void connect() {
        if (!props.enabled()) {
            log.info("Bao dong dang tat (app.alarm.enabled=false), khong ket noi MQTT cho coi");
            return;
        }
        try {
            MqttClient c = MqttClients.create(mqtt, "alarm");
            c.setCallback(this);
            // Gan truoc khi connect: connectComplete chay tren luong callback va
            // co the den truoc khi connect() tra ve.
            client = c;
        } catch (Exception e) {
            log.error("[alarm] Khong tao duoc MQTT client: {}. Coi se khong nhan duoc lenh.", e.getMessage());
            return;
        }
        MqttClients.connectWithRetry(client, MqttClients.options(mqtt), scheduler, "alarm", log);
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        try {
            client.subscribe(new String[]{STATUS_SUB, STATE_SUB}, new int[]{1, 1});
            log.info("[alarm] Da subscribe {} va {} (reconnect={})", STATUS_SUB, STATE_SUB, reconnect);
        } catch (Exception e) {
            log.error("[alarm] Subscribe that bai: {}", e.getMessage());
        }
        // AlarmService nhan su kien nay tren luong rieng va publish lai armed.
        // Khong publish o day: dang o luong callback cua Paho.
        events.publishEvent(new AlarmMessages.BrokerConnected(reconnect));
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        String id = MqttClients.deviceIdFromTopic(topic);
        if (id == null) {
            return;
        }
        Instant now = Instant.now();
        if (topic.endsWith("/status")) {
            events.publishEvent(new AlarmMessages.SirenStatus(id, payload, message.isRetained(), now));
        } else if (topic.endsWith("/state")) {
            events.publishEvent(new AlarmMessages.SirenState(id, payload, message.isRetained(), now));
        }
    }

    @Override
    public boolean sendCommand(String sirenId, String json) {
        return publish(cmdTopic(sirenId), json, false);
    }

    @Override
    public boolean publishArmed(boolean armed) {
        return publish(ARMED_TOPIC, armed ? "ARMED" : "DISARMED", true);
    }

    private boolean publish(String topic, String payload, boolean retained) {
        MqttClient c = client;
        if (c == null || !c.isConnected()) {
            log.warn("[alarm] Khong gui duoc {} -> {}: chua ket noi broker", topic, payload);
            return false;
        }
        try {
            // MqttClient.publish dong bo: voi QoS 1 no cho PUBACK, gioi han boi setTimeToWait.
            c.publish(topic, payload.getBytes(StandardCharsets.UTF_8), props.qos(), retained);
            log.debug("[alarm] {} -> {}", topic, payload);
            return true;
        } catch (Exception e) {
            log.warn("[alarm] Gui {} that bai: {}", topic, e.getMessage());
            return false;
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("[alarm] Mat ket noi MQTT: {}. Paho se tu thu lai.",
                cause == null ? "khong ro" : cause.getMessage());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    @PreDestroy
    public void disconnect() {
        try {
            MqttClient c = client;
            if (c != null && c.isConnected()) {
                c.disconnect();
                c.close();
            }
        } catch (Exception e) {
            log.warn("[alarm] Loi khi dong ket noi MQTT: {}", e.getMessage());
        }
    }
}
