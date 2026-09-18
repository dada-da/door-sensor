package dev.dada.doorsensor.mqtt;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DevicePayload(
        @JsonAlias({"device_id", "deviceId", "id"}) String deviceId,
        @JsonAlias({"state", "door", "value"}) String state,
        @JsonAlias({"ts", "timestamp", "occurredAt"}) Long ts,
        @JsonAlias({"battery_mv", "batteryMv", "vbat"}) Integer batteryMv,
        @JsonAlias({"rssi", "signal"}) Integer rssi,
        @JsonAlias({"boot_count", "bootCount"}) Integer bootCount,
        @JsonAlias({"label", "name"}) String label
) {
}
