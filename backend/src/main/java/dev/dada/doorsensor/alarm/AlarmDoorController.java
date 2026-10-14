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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bao dong rieng tung cua: cong tac, khung gio, thao tac tay.
 *
 * <pre>
 * GET  /api/alarm/doors                    moi cua, kem luat va trang thai bao ve hien tai
 * PUT  /api/alarm/doors/{id}               {"sirenEnabled":true,"startTime":"22:00","endTime":"06:00","days":[1,2,3,4,5,6,7]}
 * POST /api/alarm/doors/{id}/arm           bao ve ngay, het hieu luc o ranh gioi khung gio ke tiep
 * POST /api/alarm/doors/{id}/disarm        tat bao ve toi ranh gioi ke tiep
 * POST /api/alarm/doors/{id}/auto          bo thao tac tay, theo lich
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

    @PostMapping("/{deviceId}/arm")
    @Transactional
    public ResponseEntity<DoorRuleView> arm(@PathVariable String deviceId) {
        return override(deviceId, AlarmSchedule.Override.ARMED);
    }

    @PostMapping("/{deviceId}/disarm")
    @Transactional
    public ResponseEntity<DoorRuleView> disarm(@PathVariable String deviceId) {
        return override(deviceId, AlarmSchedule.Override.DISARMED);
    }

    @PostMapping("/{deviceId}/auto")
    @Transactional
    public ResponseEntity<DoorRuleView> auto(@PathVariable String deviceId) {
        return override(deviceId, AlarmSchedule.Override.NONE);
    }

    private ResponseEntity<DoorRuleView> override(String deviceId, AlarmSchedule.Override mode) {
        Device device = devices.findById(deviceId).orElse(null);
        if (device == null) {
            return ResponseEntity.notFound().build();
        }
        AlarmDoorRule rule = rules.effective(deviceId);
        rule.setOverrideMode(mode);
        // Thao tac tay khong keo dai vo han: tat bao ve roi quen bat lai la loi
        // thuong gap nhat cua he thong bao dong that.
        rule.setOverrideUntil(mode == AlarmSchedule.Override.NONE
                ? null
                : decisions.overrideExpiry(rule).toInstant());
        rule.touch();
        rule = rules.save(rule);
        events.publishEvent(new AlarmMessages.ScheduleChanged(deviceId, mode == AlarmSchedule.Override.DISARMED));
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
                r.getOverrideMode().name(),
                r.getOverrideUntil(),
                r.isSirenEnabled() && decisions.isArmed(r),
                decisions.inWindow(r, Instant.now()));
    }
}
