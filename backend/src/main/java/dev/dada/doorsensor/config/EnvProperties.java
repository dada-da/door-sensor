package dev.dada.doorsensor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cau hinh thu du lieu moi truong. Broker dung chung app.mqtt.
 *
 * @param enabled        tat thi khong mo ket noi MQTT nao cho phan nay
 * @param telemetryTopic chu de dang ky, dang home/env/+/telemetry
 * @param qos            muc QoS dang ky. Node gui QoS 0 (gioi han PubSubClient)
 *                       nen dat 1 o day chi co tac dung o chang broker toi may chu
 */
@ConfigurationProperties(prefix = "app.env")
public record EnvProperties(
        boolean enabled,
        String telemetryTopic,
        int qos
) {
}
