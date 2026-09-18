package dev.dada.doorsensor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "device")
public class Device {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "label", nullable = false, length = 128)
    private String label;

    @Column(name = "location", length = 128)
    private String location;

    @Column(name = "first_seen", nullable = false)
    private Instant firstSeen = Instant.now();

    @Column(name = "last_seen")
    private Instant lastSeen;

    @Column(name = "online", nullable = false)
    private boolean online;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_state", length = 16)
    private DoorState lastState;

    @Column(name = "last_state_at")
    private Instant lastStateAt;

    @Column(name = "last_battery_mv")
    private Integer lastBatteryMv;

    @Column(name = "last_rssi")
    private Integer lastRssi;

    @Column(name = "boot_count")
    private Integer bootCount;

    protected Device() {
    }

    public Device(String id, String label) {
        this.id = id;
        this.label = label;
        this.firstSeen = Instant.now();
    }

    public String getId() { return id; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public Instant getFirstSeen() { return firstSeen; }

    public Instant getLastSeen() { return lastSeen; }
    public void setLastSeen(Instant lastSeen) { this.lastSeen = lastSeen; }

    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }

    public DoorState getLastState() { return lastState; }
    public void setLastState(DoorState lastState) { this.lastState = lastState; }

    public Instant getLastStateAt() { return lastStateAt; }
    public void setLastStateAt(Instant lastStateAt) { this.lastStateAt = lastStateAt; }

    public Integer getLastBatteryMv() { return lastBatteryMv; }
    public void setLastBatteryMv(Integer lastBatteryMv) { this.lastBatteryMv = lastBatteryMv; }

    public Integer getLastRssi() { return lastRssi; }
    public void setLastRssi(Integer lastRssi) { this.lastRssi = lastRssi; }

    public Integer getBootCount() { return bootCount; }
    public void setBootCount(Integer bootCount) { this.bootCount = bootCount; }
}
