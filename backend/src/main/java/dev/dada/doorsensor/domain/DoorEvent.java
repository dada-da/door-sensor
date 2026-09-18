package dev.dada.doorsensor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "door_event")
public class DoorEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    private DoorState state;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    @Column(name = "battery_mv")
    private Integer batteryMv;

    @Column(name = "rssi")
    private Integer rssi;

    @Column(name = "boot_count")
    private Integer bootCount;

    @Column(name = "duration_ms")
    private Long durationMs;

    protected DoorEvent() {
    }

    public DoorEvent(String deviceId, DoorState state, Instant occurredAt, Instant receivedAt) {
        this.deviceId = deviceId;
        this.state = state;
        this.occurredAt = occurredAt;
        this.receivedAt = receivedAt;
    }

    public Long getId() { return id; }
    public String getDeviceId() { return deviceId; }
    public DoorState getState() { return state; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getReceivedAt() { return receivedAt; }

    public Integer getBatteryMv() { return batteryMv; }
    public void setBatteryMv(Integer batteryMv) { this.batteryMv = batteryMv; }

    public Integer getRssi() { return rssi; }
    public void setRssi(Integer rssi) { this.rssi = rssi; }

    public Integer getBootCount() { return bootCount; }
    public void setBootCount(Integer bootCount) { this.bootCount = bootCount; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public long getLagMs() {
        return receivedAt.toEpochMilli() - occurredAt.toEpochMilli();
    }
}
