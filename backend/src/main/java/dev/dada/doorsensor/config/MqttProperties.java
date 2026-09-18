package dev.dada.doorsensor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mqtt")
public record MqttProperties(
        String brokerUrl,
        String clientId,
        String username,
        String password,
        String stateTopic,
        String statusTopic,
        int qos,
        boolean automaticReconnect,
        boolean cleanSession,
        int connectionTimeoutSeconds,
        int keepAliveSeconds
) {
}
