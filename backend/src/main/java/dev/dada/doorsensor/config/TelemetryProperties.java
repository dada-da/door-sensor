package dev.dada.doorsensor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ghi nhat ky moi lan thuc cua thiet bi cua (bang device_telemetry).
 *
 * @param enabled    tat thi khong mo ket noi MQTT nao cho phan nay
 * @param stateTopic chu de dang ky, mac dinh trung app.mqtt.state-topic
 * @param qos        muc QoS dang ky
 */
@ConfigurationProperties(prefix = "app.telemetry")
public record TelemetryProperties(
        boolean enabled,
        String stateTopic,
        int qos
) {
}
