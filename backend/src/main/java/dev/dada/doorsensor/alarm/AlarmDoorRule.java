package dev.dada.doorsensor.alarm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Luat bao ve cua mot cua (V5, V6).
 *
 * <p>Khung gio co hai vai tro, gop lam mot theo yeu cau 14/10:
 * <ul>
 *   <li>trong khung, cua mo thi coi keu (neu {@code sirenEnabled});</li>
 *   <li>tin Telegram mo cua ghi "ngoai khung gio thuong le".</li>
 * </ul>
 *
 * <p>Cua chua co dong nao dung {@link #defaults(String)}: kich hoat coi, 00:00 den
 * 07:00 moi ngay. Cua moi lap sau nay duoc bao ve ngay ma khong phai nho bat.
 */
@Entity
@Table(name = "alarm_door_rule")
public class AlarmDoorRule implements ArmingRule {

    public static final LocalTime DEFAULT_START = LocalTime.of(0, 0);
    public static final LocalTime DEFAULT_END = LocalTime.of(7, 0);
    public static final String ALL_DAYS = "1,2,3,4,5,6,7";

    @Id
    @Column(name = "device_id", length = 64)
    private String deviceId;

    @Column(name = "siren_enabled", nullable = false)
    private boolean sirenEnabled = true;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime = DEFAULT_START;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime = DEFAULT_END;

    /** Luu dang "1,2,3", 1 la thu Hai theo ISO-8601. Ngay nao khung gio BAT DAU. */
    @Column(name = "days_of_week", nullable = false, length = 20)
    private String daysOfWeek = ALL_DAYS;

    @Enumerated(EnumType.STRING)
    @Column(name = "override_mode", nullable = false, length = 10)
    private AlarmSchedule.Override overrideMode = AlarmSchedule.Override.NONE;

    @Column(name = "override_until")
    private Instant overrideUntil;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected AlarmDoorRule() {
    }

    public AlarmDoorRule(String deviceId, boolean sirenEnabled) {
        this.deviceId = deviceId;
        this.sirenEnabled = sirenEnabled;
        this.updatedAt = Instant.now();
    }

    /** Luat mac dinh cho cua chua cau hinh. Chua luu, goi save neu muon giu. */
    public static AlarmDoorRule defaults(String deviceId) {
        return new AlarmDoorRule(deviceId, true);
    }

    @Override
    public ScheduleWindow toWindow() {
        // "enabled" cua khung gio luon true: bat tat bao dong la sirenEnabled, con
        // khung gio van co nghia cho nhan "ngoai gio thuong le" ke ca khi tat coi.
        return new ScheduleWindow(true, startTime, endTime, parseDays());
    }

    public Set<DayOfWeek> parseDays() {
        if (daysOfWeek == null || daysOfWeek.isBlank()) {
            return EnumSet.noneOf(DayOfWeek.class);
        }
        return Arrays.stream(daysOfWeek.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .map(DayOfWeek::of)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
    }

    public void setDays(Set<DayOfWeek> days) {
        this.daysOfWeek = days.stream()
                .sorted()
                .map(d -> String.valueOf(d.getValue()))
                .collect(Collectors.joining(","));
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public String getDeviceId() { return deviceId; }

    public boolean isSirenEnabled() { return sirenEnabled; }
    public void setSirenEnabled(boolean sirenEnabled) { this.sirenEnabled = sirenEnabled; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    @Override
    public AlarmSchedule.Override getOverrideMode() { return overrideMode; }
    public void setOverrideMode(AlarmSchedule.Override overrideMode) { this.overrideMode = overrideMode; }

    @Override
    public Instant getOverrideUntil() { return overrideUntil; }
    public void setOverrideUntil(Instant overrideUntil) { this.overrideUntil = overrideUntil; }

    public Instant getUpdatedAt() { return updatedAt; }
}
