package dev.dada.doorsensor.alarm;

import dev.dada.doorsensor.domain.Device;
import dev.dada.doorsensor.repo.DeviceRepository;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Chon cua nao kich hoat coi.
 *
 * <pre>
 * GET /api/alarm/doors                 moi cua kem sirenEnabled
 * PUT /api/alarm/doors/door-01         {"sirenEnabled": false}
 * </pre>
 *
 * Cua tat coi van gui Telegram mo cua binh thuong (NotificationService), chi
 * khong lam coi keu va khong co tin "BAO DONG".
 */
@RestController
@RequestMapping("/api/alarm/doors")
public class AlarmDoorController {

    private final DeviceRepository devices;
    private final AlarmDoorRuleRepository rules;

    public AlarmDoorController(DeviceRepository devices, AlarmDoorRuleRepository rules) {
        this.devices = devices;
        this.rules = rules;
    }

    public record DoorRuleView(String deviceId, String label, boolean sirenEnabled, Instant updatedAt) {
    }

    public record DoorRuleUpdate(@NotNull Boolean sirenEnabled) {
    }

    @GetMapping
    public List<DoorRuleView> list() {
        return devices.findAllByOrderByLabelAsc().stream()
                .map(d -> {
                    AlarmDoorRule r = rules.findById(d.getId()).orElse(null);
                    return new DoorRuleView(d.getId(), d.getLabel(),
                            r == null || r.isSirenEnabled(), r == null ? null : r.getUpdatedAt());
                })
                .toList();
    }

    @PutMapping("/{deviceId}")
    @Transactional
    public ResponseEntity<DoorRuleView> update(@PathVariable String deviceId, @RequestBody DoorRuleUpdate body) {
        if (body == null || body.sirenEnabled() == null) {
            return ResponseEntity.badRequest().build();
        }
        Device device = devices.findById(deviceId).orElse(null);
        if (device == null) {
            // Chi cau hinh cua da tung gui tin: tranh go nham ma thiet bi roi tuong da tat duoc.
            return ResponseEntity.notFound().build();
        }
        AlarmDoorRule rule = rules.findById(deviceId)
                .orElseGet(() -> new AlarmDoorRule(deviceId, true));
        rule.setSirenEnabled(body.sirenEnabled());
        rule = rules.save(rule);
        return ResponseEntity.ok(new DoorRuleView(deviceId, device.getLabel(),
                rule.isSirenEnabled(), rule.getUpdatedAt()));
    }
}
