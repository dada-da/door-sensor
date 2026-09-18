package dev.dada.doorsensor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.device")
public record DeviceProperties(
        long offlineAfterSeconds,
        long watchdogIntervalMs
) {
}
