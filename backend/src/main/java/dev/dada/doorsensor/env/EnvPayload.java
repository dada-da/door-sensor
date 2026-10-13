package dev.dada.doorsensor.env;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Thong diep tren home/env/{id}/telemetry.
 *
 * <pre>
 * { "temp_c": 27.41, "rh": 63.2, "ts": 1760198400 }
 * </pre>
 *
 * Bo qua truong la, giong DevicePayload, de firmware them truong do dac sau nay
 * khong phai sua may chu.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EnvPayload(
        @JsonAlias({"temp_c", "temperature", "temp", "t"}) Double temperatureC,
        @JsonAlias({"rh", "humidity", "hum", "h"}) Double humidityPct,
        @JsonAlias({"ts", "timestamp"}) Long ts
) {
}
