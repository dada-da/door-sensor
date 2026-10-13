package dev.dada.doorsensor.mqtt;

import dev.dada.doorsensor.config.MqttProperties;
import java.time.Duration;
import java.time.Instant;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.springframework.scheduling.TaskScheduler;

/**
 * Tao MQTT client cho cac thanh phan bo sung (moi truong, bao dong).
 *
 * <p>Moi thanh phan bo sung co client rieng, ket noi rieng, khong dung chung
 * client cua {@link MqttIngestService}. Ly do: duong nhan su kien cua tinh tu
 * T0 khong bi dong vao. Thanh phan moi co loi, mat ket noi hay bi treo cung
 * khong keo theo duong nhan cu.
 *
 * <p>Chi doc {@link MqttProperties}, khong sua no, de cau hinh broker van o mot
 * cho duy nhat.
 */
public final class MqttClients {

    /** Khoang cho giua hai lan thu ket noi lan dau. */
    public static final Duration INITIAL_RETRY = Duration.ofSeconds(30);

    private MqttClients() {
    }

    /**
     * @param suffix phan duoi client id, vi du "env", "alarm". Broker yeu cau
     *               client id khac nhau, trung id thi ket noi sau da ket noi truoc.
     */
    public static MqttClient create(MqttProperties props, String suffix) throws MqttException {
        String id = props.clientId() + "-" + suffix + "-" + System.currentTimeMillis();
        MqttClient client = new MqttClient(props.brokerUrl(), id, new MemoryPersistence());
        // Gioi han thoi gian cho moi thao tac dong bo (publish QoS 1, subscribe).
        // Khong dat thi publish khi broker treo se cho vo han va khoa luon luong goi.
        client.setTimeToWait(5000);
        return client;
    }

    public static MqttConnectOptions options(MqttProperties props) {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(props.automaticReconnect());
        options.setCleanSession(props.cleanSession());
        options.setConnectionTimeout(props.connectionTimeoutSeconds());
        options.setKeepAliveInterval(props.keepAliveSeconds());
        if (props.username() != null && !props.username().isBlank()) {
            options.setUserName(props.username());
            options.setPassword(props.password() == null ? new char[0] : props.password().toCharArray());
        }
        return options;
    }

    /**
     * Ket noi lan dau, that bai thi hen thu lai cho toi khi duoc.
     *
     * <p>Can thiet vi automaticReconnect cua Paho chi co tac dung sau khi da ket
     * noi thanh cong mot lan. Pi mat dien roi khoi dong lai, ung dung len truoc
     * Mosquitto thi lan connect dau that bai va client nam im mai mai.
     *
     * @param tag ten ngan de ghi nhat ky, vi du "env"
     */
    public static void connectWithRetry(MqttClient client, MqttConnectOptions options,
                                        TaskScheduler scheduler, String tag, Logger log) {
        try {
            client.connect(options);
            log.info("[{}] Da ket noi MQTT broker", tag);
        } catch (Exception e) {
            log.error("[{}] Khong ket noi duoc MQTT broker: {}. Thu lai sau {} giay.",
                    tag, e.getMessage(), INITIAL_RETRY.getSeconds());
            scheduler.schedule(() -> connectWithRetry(client, options, scheduler, tag, log),
                    Instant.now().plus(INITIAL_RETRY));
        }
    }

    /** Ma thiet bi la phan tu thu ba cua chu de: home/{loai}/{id}/{kenh}. */
    public static String deviceIdFromTopic(String topic) {
        String[] parts = topic.split("/");
        return parts.length >= 4 && !parts[2].isBlank() ? parts[2] : null;
    }
}
