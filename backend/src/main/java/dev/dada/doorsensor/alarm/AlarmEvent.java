package dev.dada.doorsensor.alarm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Mot dot coi keu. Gia han trong luc dang keu cap nhat chinh dong nay chu khong
 * tao dong moi, nen mot dong tuong ung mot khoang coi keu lien tuc.
 *
 * <p>Ba moc thoi gian cho phep tach do tre bao dong (PCN-08, bang 5.10) thanh
 * chang: {@code doorOccurredAt} (node cua ghi nhan), {@code triggeredAt} (may chu
 * phat lenh), {@code ackedAt} (node coi bao da keu, theo dong ho may chu).
 */
@Entity
@Table(name = "alarm_event")
public class AlarmEvent {

    public enum StopSource { TIMEOUT, REMOTE, OVERRIDE, TELEGRAM }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Thiet bi gay ra: cua kich hoat, hoac chinh node coi voi lenh thu. */
    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Column(name = "siren_id", length = 64)
    private String sirenId;

    @Column(name = "door_occurred_at")
    private Instant doorOccurredAt;

    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt;

    @Column(name = "duration_ms", nullable = false)
    private int durationMs;

    @Column(name = "reason", nullable = false, length = 32)
    private String reason;

    /** Lenh co toi duoc broker khong (PUBACK cua QoS 1). */
    @Column(name = "command_ok", nullable = false)
    private boolean commandOk = true;

    @Column(name = "acked_at")
    private Instant ackedAt;

    @Column(name = "stopped_at")
    private Instant stoppedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "stop_source", length = 16)
    private StopSource stopSource;

    protected AlarmEvent() {
    }

    public AlarmEvent(String deviceId, String sirenId, Instant doorOccurredAt,
                      Instant triggeredAt, int durationMs, String reason, boolean commandOk) {
        this.deviceId = deviceId;
        this.sirenId = sirenId;
        this.doorOccurredAt = doorOccurredAt;
        this.triggeredAt = triggeredAt;
        this.durationMs = durationMs;
        this.reason = reason;
        this.commandOk = commandOk;
    }

    /** Thoi diem coi du kien tu tat neu khong ai dung. */
    public Instant expectedEnd() {
        return triggeredAt.plusMillis(durationMs);
    }

    public void stop(Instant at, StopSource source) {
        this.stoppedAt = at;
        this.stopSource = source;
    }

    public Long getId() { return id; }
    public String getDeviceId() { return deviceId; }
    public String getSirenId() { return sirenId; }
    public Instant getDoorOccurredAt() { return doorOccurredAt; }
    public Instant getTriggeredAt() { return triggeredAt; }
    public String getReason() { return reason; }
    public boolean isCommandOk() { return commandOk; }
    public Instant getStoppedAt() { return stoppedAt; }
    public StopSource getStopSource() { return stopSource; }

    public int getDurationMs() { return durationMs; }
    public void setDurationMs(int durationMs) { this.durationMs = durationMs; }

    public Instant getAckedAt() { return ackedAt; }
    public void setAckedAt(Instant ackedAt) { this.ackedAt = ackedAt; }
}
