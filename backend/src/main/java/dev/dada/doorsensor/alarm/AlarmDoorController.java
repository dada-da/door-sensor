package dev.dada.doorsensor.alarm;

import dev.dada.doorsensor.domain.Device;
import dev.dada.doorsensor.repo.DeviceRepository;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bao dong rieng tung cua: che do (tat / theo lich / luon bat) va khung gio.
 *
 * <pre>
 * GET  /api/alarm/doors                    moi cua, kem luat va trang thai bao ve hien tai
 * PUT  /api/alarm/doors/{id}               {"startTime":"22:00","endTime":"06:00","days":[1,2,3,4,5,6,7]}
 * PUT  /api/alarm/doors/{id}/mode          {"mode":"OFF" | "SCHEDULE" | "ALWAYS"}, lau dai
 * </pre>
 *
 * Moi truong trong PUT deu tuy chon: gui truong nao doi truong do.
 */
@RestController
@RequestMapping("/api/alarm/doors")
public class AlarmDoorController {

    private final DeviceRepository devices;
    private final AlarmDoorRuleRepository rules;
    private final AlarmDecisionService decisions;
    private final ApplicationEventPublisher events;

    public AlarmDoorController(DeviceRepository devices, AlarmDoorRuleRepository rules,
                               AlarmDecisionService decisions, ApplicationEventPublisher events) {
        this.devices = devices;
        this.rules = rules;
        this.decisions = decisions;
        this.events = events;
    }

    public record DoorRuleView(
            String deviceId,
            String label,
            boolean online,
            String doorState,
            boolean sirenEnabled,
            LocalTime startTime,
            LocalTime endTime,
            List<Integer> days,
            /** OFF | SCHEDULE | ALWAYS. */
            String mode,
            String overrideMode,
            Instant overrideUntil,
            /** Cua nay luc nay co lam coi keu neu bi mo khong. */
            boolean armedNow,
            /** Luc nay co nam trong khung gio cua cua khong (nhan "ngoai gio thuong le"). */
            boolean inWindowNow) {
    }

    public record DoorRuleUpdate(
            Boolean sirenEnabled,
            LocalTime startTime,
            LocalTime endTime,
            List<Integer> days) {
    }

    @GetMapping
    public List<DoorRuleView> list() {
        return devices.findAllByOrderByLabelAsc().stream()
                .map(d -> view(d, rules.effective(d.getId())))
                .toList();
    }

    @PutMapping("/{deviceId}")
    @Transactional
    public ResponseEntity<DoorRuleView> update(@PathVariable String deviceId, @RequestBody DoorRuleUpdate body) {
        Device device = devices.findById(deviceId).orElse(null);
        if (device == null) {
            // Chi cau hinh cua da tung gui tin: tranh go nham ma roi tuong da tat duoc.
            return ResponseEntity.notFound().build();
        }
        if (body == null) {
            return ResponseEntity.badRequest().build();
        }
        if (body.days() != null && body.days().stream().anyMatch(d -> d == null || d < 1 || d > 7)) {
            return ResponseEntity.badRequest().build();
        }

        AlarmDoorRule rule = rules.effective(deviceId);
        boolean wasEnabled = rule.isSirenEnabled();
        if (body.sirenEnabled() != null) rule.setSirenEnabled(body.sirenEnabled());
        if (body.startTime() != null) rule.setStartTime(body.startTime());
        if (body.endTime() != null) rule.setEndTime(body.endTime());
        if (body.days() != null) {
            rule.setDays(body.days().stream().map(DayOfWeek::of).collect(Collectors.toSet()));
        }
        rule.touch();
        rule = rules.save(rule);

        events.publishEvent(new AlarmMessages.ScheduleChanged(deviceId, wasEnabled && !rule.isSirenEnabled()));
        return ResponseEntity.ok(view(device, rule));
    }

    public record ModeUpdate(AlarmDoorRule.Mode mode) {
    }

    /**
     * Cong tac bao dong lau dai: OFF (tat han), SCHEDULE (theo khung gio), ALWAYS
     * (bao ve suot ngay dem). Co hieu luc ngay, ton tai toi khi doi lai.
     */
    @PutMapping("/{deviceId}/mode")
    @Transactional
    public ResponseEntity<DoorRuleView> mode(@PathVariable String deviceId, @RequestBody ModeUpdate body) {
        Device device = devices.findById(deviceId).orElse(null);
        if (device == null) {
            return ResponseEntity.notFound().build();
        }
        if (body == null || body.mode() == null) {
            return ResponseEntity.badRequest().build();
        }
        AlarmDoorRule rule = rules.effective(deviceId);
        rule.applyMode(body.mode());
        rule.touch();
        rule = rules.save(rule);
        // Tat bao dong cua dung cai cua dang lam coi keu thi tat coi luon.
        events.publishEvent(new AlarmMessages.ScheduleChanged(deviceId, body.mode() == AlarmDoorRule.Mode.OFF));
        return ResponseEntity.ok(view(device, rule));
    }

    private DoorRuleView view(Device d, AlarmDoorRule r) {
        return new DoorRuleView(
                d.getId(),
                d.getLabel(),
                d.isOnline(),
                d.getLastState() == null ? null : d.getLastState().name(),
                r.isSirenEnabled(),
                r.getStartTime(),
                r.getEndTime(),
                r.parseDays().stream().map(DayOfWeek::getValue).sorted().toList(),
                r.mode().name(),
                r.getOverrideMode().name(),
                r.getOverrideUntil(),
                r.isSirenEnabled() && decisions.isArmed(r),
                decisions.inWindow(r, Instant.now()));
    }
}
