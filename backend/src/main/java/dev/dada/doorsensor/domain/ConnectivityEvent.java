package dev.dada.doorsensor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "connectivity_event")
public class ConnectivityEvent {

    public enum Source {
        REPORTED,
        LWT,
        WATCHDOG
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Column(name = "online", nullable = false)
    private boolean online;

    @Column(name = "source", nullable = false, length = 16)
    private String source;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    protected ConnectivityEvent() {
    }

    public ConnectivityEvent(String deviceId, boolean online, Source source) {
        this.deviceId = deviceId;
        this.online = online;
        this.source = source.name();
        this.receivedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getDeviceId() { return deviceId; }
    public boolean isOnline() { return online; }
    public String getSource() { return source; }
    public Instant getReceivedAt() { return receivedAt; }
}
