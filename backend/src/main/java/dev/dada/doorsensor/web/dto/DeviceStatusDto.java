package dev.dada.doorsensor.web.dto;

import dev.dada.doorsensor.domain.BatteryLevel;
import dev.dada.doorsensor.domain.Device;
import java.time.Duration;
import java.time.Instant;

public record DeviceStatusDto(
        String id,
        String label,
        String location,
        boolean online,
        String state,
        Instant stateSince,
        /** Cua da o trang thai hien tai bao lau, tinh bang giay. */
        Long stateForSeconds,
        Instant lastSeen,
        Integer batteryMv,
        Integer batteryPercent,
        Integer rssi,
        Integer bootCount
) {
    public static DeviceStatusDto from(Device d) {
        Long forSeconds = d.getLastStateAt() == null
                ? null
                : Duration.between(d.getLastStateAt(), Instant.now()).getSeconds();

        return new DeviceStatusDto(
                d.getId(),
                d.getLabel(),
                d.getLocation(),
                d.isOnline(),
                d.getLastState() == null ? null : d.getLastState().name(),
                d.getLastStateAt(),
                forSeconds,
                d.getLastSeen(),
                d.getLastBatteryMv(),
                BatteryLevel.percent(d.getLastBatteryMv()),
                d.getLastRssi(),
                d.getBootCount()
        );
    }
}
