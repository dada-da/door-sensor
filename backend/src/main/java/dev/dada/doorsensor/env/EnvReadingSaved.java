package dev.dada.doorsensor.env;

import java.time.Instant;

/** Phat ra sau khi mot so do moi truong da ghi vao CSDL. EnvAlertService nghe. */
public record EnvReadingSaved(String deviceId, Instant measuredAt, Double temperatureC, Double humidityPct) {
}
