package dev.dada.doorsensor.telemetry;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Cac truong do dac trong tin home/door/{id}/state. Rieng voi DevicePayload (thuoc
 * duong nhan tu T0) de khong phai sua lop do.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelemetryPayload(
        String state,
        String wake,
        @JsonAlias({"battery_mv", "batteryMv", "vbat"}) Integer batteryMv,
        @JsonAlias({"batt_adc_mv"}) Integer battAdcMv,
        Integer rssi,
        @JsonAlias({"boot_count", "bootCount"}) Long bootCount,
        @JsonAlias({"work_ms"}) Integer workMs,
        @JsonAlias({"wifi_ms"}) Integer wifiMs,
        @JsonAlias({"mqtt_ms"}) Integer mqttMs,
        @JsonAlias({"last_rtt_ms"}) Integer rttMs,
        Long ts
) {
}
