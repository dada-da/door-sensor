package dev.dada.doorsensor.alarm;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cau hinh bao dong dung chung cho ca nha. Chi con thoi luong coi, vi chi co mot
 * coi. Khung gio va thao tac tay da chuyen sang tung cua (AlarmDoorController).
 *
 * <pre>
 * GET /api/alarm/settings     {"sirenSeconds":30}
 * PUT /api/alarm/settings     {"sirenSeconds":45}
 * </pre>
 */
@RestController
@RequestMapping("/api/alarm/settings")
public class AlarmSettingsController {

    private final AlarmScheduleRepository repo;
    private final ApplicationEventPublisher events;

    public AlarmSettingsController(AlarmScheduleRepository repo, ApplicationEventPublisher events) {
        this.repo = repo;
        this.events = events;
    }

    public record Settings(
            @Min(1) @Max(AlarmDecisionService.MAX_SIREN_SECONDS) int sirenSeconds) {
    }

    @GetMapping
    public Settings get() {
        return new Settings(repo.current().getSirenSeconds());
    }

    @PutMapping
    @Transactional
    public Settings update(@Valid @RequestBody Settings body) {
        AlarmSchedule s = repo.current();
        s.setSirenSeconds(body.sirenSeconds());
        s.setUpdatedAt(Instant.now());
        repo.save(s);
        events.publishEvent(new AlarmMessages.ScheduleChanged(null, false));
        return new Settings(s.getSirenSeconds());
    }
}
