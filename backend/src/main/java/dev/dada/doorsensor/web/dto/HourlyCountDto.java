package dev.dada.doorsensor.web.dto;

import java.time.Instant;

public record HourlyCountDto(Instant hour, long opens) {
}
