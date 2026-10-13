package dev.dada.doorsensor.alarm;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/alarm/schedule")
public class AlarmScheduleController {

    private final AlarmScheduleRepository repo;
    private final AlarmDecisionService decisions;
    private final ApplicationEventPublisher events;

    public AlarmScheduleController(AlarmScheduleRepository repo, AlarmDecisionService decisions,
                                   ApplicationEventPublisher events) {
        this.repo = repo;
        this.decisions = decisions;
        this.events = events;
    }

    public record ScheduleView(
            boolean enabled,
            LocalTime startTime,
            LocalTime endTime,
            int sirenSeconds,
            List<Integer> days,
            String overrideMode,
            Instant overrideUntil,
            boolean armedNow) {
    }

    public record ScheduleUpdate(
            @NotNull Boolean enabled,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @Min(1) @Max(AlarmDecisionService.MAX_SIREN_SECONDS) int sirenSeconds,
            @NotNull List<Integer> days) {
    }

    @GetMapping
    public ScheduleView get() {
        return toView(repo.current());
    }

    @PutMapping
    @Transactional
    public ScheduleView update(@RequestBody ScheduleUpdate body) {
        AlarmSchedule s = repo.current();
        s.setEnabled(body.enabled());
        s.setStartTime(body.startTime());
        s.setEndTime(body.endTime());
        s.setSirenSeconds(body.sirenSeconds());
        s.setDays(body.days().stream()
                .map(DayOfWeek::of)
                .collect(Collectors.toSet()));
        s.setUpdatedAt(Instant.now());
        ScheduleView view = toView(repo.save(s));
        // AlarmService nghe sau khi giao dich commit, cap nhat home/alarm/armed ngay.
        events.publishEvent(new AlarmMessages.ScheduleChanged(false));
        return view;
    }

    /** Bat bao ve ngay, het hieu luc o ranh gioi khung gio ke tiep. */
    @PostMapping("/arm")
    @Transactional
    public ScheduleView arm() {
        return applyOverride(AlarmSchedule.Override.ARMED);
    }

    /** Tat bao ve, het hieu luc o ranh gioi khung gio ke tiep. */
    @PostMapping("/disarm")
    @Transactional
    public ScheduleView disarm() {
        return applyOverride(AlarmSchedule.Override.DISARMED);
    }

    /** Bo thao tac tay, quay ve theo lich ngay lap tuc. */
    @PostMapping("/auto")
    @Transactional
    public ScheduleView auto() {
        AlarmSchedule s = repo.current();
        s.setOverrideMode(AlarmSchedule.Override.NONE);
        s.setOverrideUntil(null);
        s.setUpdatedAt(Instant.now());
        ScheduleView view = toView(repo.save(s));
        events.publishEvent(new AlarmMessages.ScheduleChanged(false));
        return view;
    }

    private ScheduleView applyOverride(AlarmSchedule.Override mode) {
        AlarmSchedule s = repo.current();
        s.setOverrideMode(mode);
        // Quy tac: thao tac tay khong keo dai vo han. Tat bao ve roi quen bat lai
        // la loi thuong gap nhat cua he thong bao dong that.
        s.setOverrideUntil(decisions.overrideExpiry(s).toInstant());
        s.setUpdatedAt(Instant.now());
        ScheduleView view = toView(repo.save(s));
        // Tat bao ve trong luc coi dang keu thi AlarmService tat luon coi.
        events.publishEvent(new AlarmMessages.ScheduleChanged(mode == AlarmSchedule.Override.DISARMED));
        return view;
    }

    private ScheduleView toView(AlarmSchedule s) {
        Set<DayOfWeek> days = s.parseDays();
        return new ScheduleView(
                s.isEnabled(),
                s.getStartTime(),
                s.getEndTime(),
                s.getSirenSeconds(),
                days.stream().map(DayOfWeek::getValue).sorted().toList(),
                s.getOverrideMode().name(),
                s.getOverrideUntil(),
                decisions.isArmed(s));
    }
}
