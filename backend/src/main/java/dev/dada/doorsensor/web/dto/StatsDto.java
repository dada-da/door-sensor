package dev.dada.doorsensor.web.dto;

public record StatsDto(
        long opensToday,
        long opens24h,
        double avgLagMs,
        long openSeconds24h,
        long disconnects24h
) {
}
