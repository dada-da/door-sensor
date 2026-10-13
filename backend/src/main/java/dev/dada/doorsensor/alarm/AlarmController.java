package dev.dada.doorsensor.alarm;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API dieu khien bao dong. Lich bao ve o AlarmScheduleController (/api/alarm/schedule).
 */
@RestController
@RequestMapping("/api/alarm")
public class AlarmController {

    private final AlarmService alarm;
    private final AlarmEventRepository events;

    public AlarmController(AlarmService alarm, AlarmEventRepository events) {
        this.alarm = alarm;
        this.events = events;
    }

    public record EventView(Long id, String deviceId, String sirenId, String reason,
                            Instant doorOccurredAt, Instant triggeredAt, Instant ackedAt,
                            int durationMs, boolean commandOk,
                            Instant stoppedAt, String stopSource,
                            Long doorToCommandMs, Long commandToAckMs) {
        static EventView from(AlarmEvent e) {
            return new EventView(
                    e.getId(), e.getDeviceId(), e.getSirenId(), e.getReason(),
                    e.getDoorOccurredAt(), e.getTriggeredAt(), e.getAckedAt(),
                    e.getDurationMs(), e.isCommandOk(),
                    e.getStoppedAt(), e.getStopSource() == null ? null : e.getStopSource().name(),
                    millisBetween(e.getDoorOccurredAt(), e.getTriggeredAt()),
                    millisBetween(e.getTriggeredAt(), e.getAckedAt()));
        }

        private static Long millisBetween(Instant a, Instant b) {
            return (a == null || b == null) ? null : b.toEpochMilli() - a.toEpochMilli();
        }
    }

    @GetMapping("/status")
    public AlarmStatusDto status() {
        return alarm.status();
    }

    /** Tat tieng coi dang keu. Khong doi che do bao ve. */
    @PostMapping("/stop")
    public ResponseEntity<AlarmStatusDto> stop() {
        return respond(alarm.stop());
    }

    /** Coi keu 200 ms de xac nhan duong lenh con thong. */
    @PostMapping("/test")
    public ResponseEntity<AlarmStatusDto> test() {
        return respond(alarm.test(false));
    }

    /** Lich su coi keu, kem do tre tung chang cho bang 5.10. */
    @GetMapping("/events")
    public List<EventView> events(@RequestParam(defaultValue = "50") int size) {
        int capped = Math.min(Math.max(size, 1), 500);
        return events.findAllByOrderByTriggeredAtDesc(PageRequest.of(0, capped)).stream()
                .map(EventView::from)
                .toList();
    }

    private ResponseEntity<AlarmStatusDto> respond(AlarmService.CommandResult r) {
        HttpStatus code = switch (r) {
            case OK -> HttpStatus.OK;
            case BUSY -> HttpStatus.CONFLICT;
            case SEND_FAILED, DISABLED -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return ResponseEntity.status(code).body(alarm.status());
    }
}
