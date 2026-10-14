package dev.dada.doorsensor.alarm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Cua co kich hoat coi hay khong. Khong co dong nghia la co (xem V5).
 */
@Entity
@Table(name = "alarm_door_rule")
public class AlarmDoorRule {

    @Id
    @Column(name = "device_id", length = 64)
    private String deviceId;

    @Column(name = "siren_enabled", nullable = false)
    private boolean sirenEnabled = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected AlarmDoorRule() {
    }

    public AlarmDoorRule(String deviceId, boolean sirenEnabled) {
        this.deviceId = deviceId;
        this.sirenEnabled = sirenEnabled;
        this.updatedAt = Instant.now();
    }

    public String getDeviceId() { return deviceId; }

    public boolean isSirenEnabled() { return sirenEnabled; }

    public void setSirenEnabled(boolean sirenEnabled) {
        this.sirenEnabled = sirenEnabled;
        this.updatedAt = Instant.now();
    }

    public Instant getUpdatedAt() { return updatedAt; }
}
