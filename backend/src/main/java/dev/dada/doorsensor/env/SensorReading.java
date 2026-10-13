package dev.dada.doorsensor.env;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Mot lan do moi truong. Chuoi so do lien tuc, khac ban chat su kien roi rac
 * cua door_event nen nam o bang rieng.
 */
@Entity
@Table(name = "sensor_reading")
public class SensorReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "temperature_c")
    private Double temperatureC;

    @Column(name = "humidity_pct")
    private Double humidityPct;

    protected SensorReading() {
    }

    public SensorReading(String deviceId, Instant measuredAt, Instant receivedAt,
                         Double temperatureC, Double humidityPct) {
        this.deviceId = deviceId;
        this.measuredAt = measuredAt;
        this.receivedAt = receivedAt;
        this.temperatureC = temperatureC;
        this.humidityPct = humidityPct;
    }

    public Long getId() { return id; }
    public String getDeviceId() { return deviceId; }
    public Instant getMeasuredAt() { return measuredAt; }
    public Instant getReceivedAt() { return receivedAt; }
    public Double getTemperatureC() { return temperatureC; }
    public Double getHumidityPct() { return humidityPct; }
}
