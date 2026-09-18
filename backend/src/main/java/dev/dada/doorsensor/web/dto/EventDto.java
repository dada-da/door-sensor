package dev.dada.doorsensor.web.dto;

import dev.dada.doorsensor.domain.DoorEvent;
import java.time.Instant;

public record EventDto(
        Long id,
        String deviceId,
        String state,
        Instant occurredAt,
        Instant receivedAt,
        Long lagMs,
        Long durationMs,
        Integer batteryMv,
        Integer rssi
) {
    public static EventDto from(DoorEvent e) {
        return new EventDto(
                e.getId(),
                e.getDeviceId(),
                e.getState().name(),
                e.getOccurredAt(),
                e.getReceivedAt(),
                e.getLagMs(),
                e.getDurationMs(),
                e.getBatteryMv(),
                e.getRssi()
        );
    }
}
