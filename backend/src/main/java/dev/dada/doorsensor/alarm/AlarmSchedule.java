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
 * Cau hinh bao dong. Mot dong duy nhat, id co dinh bang 1.
 */
@Entity
@Table(name = "alarm_schedule")
public class AlarmSchedule {

    public enum Override { NONE, ARMED, DISARMED }

    @Id
    private Short id = 1;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime = LocalTime.of(0, 0);

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime = LocalTime.of(7, 0);

    @Column(name = "siren_seconds", nullable = false)
    private int sirenSeconds = 30;

    /** Luu dang "1,2,3", 1 la thu Hai theo chuan ISO-8601. */
    @Column(name = "days_of_week", nullable = false, length = 20)
    private String daysOfWeek = "1,2,3,4,5,6,7";

    @Enumerated(EnumType.STRING)
    @Column(name = "override_mode", nullable = false, length = 10)
    private Override overrideMode = Override.NONE;

    @Column(name = "override_until")
    private Instant overrideUntil;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    /** Chuyen sang dang thuan de kiem thu va de tinh toan, khong dinh JPA. */
    public ScheduleWindow toWindow() {
        return new ScheduleWindow(enabled, startTime, endTime, parseDays());
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

    public Short getId() { return id; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public int getSirenSeconds() { return sirenSeconds; }
    public void setSirenSeconds(int sirenSeconds) { this.sirenSeconds = sirenSeconds; }

    public String getDaysOfWeek() { return daysOfWeek; }
    public void setDaysOfWeek(String daysOfWeek) { this.daysOfWeek = daysOfWeek; }

    public Override getOverrideMode() { return overrideMode; }
    public void setOverrideMode(Override overrideMode) { this.overrideMode = overrideMode; }

    public Instant getOverrideUntil() { return overrideUntil; }
    public void setOverrideUntil(Instant overrideUntil) { this.overrideUntil = overrideUntil; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
