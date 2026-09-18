package dev.dada.doorsensor.web;

import dev.dada.doorsensor.domain.DoorEvent;
import dev.dada.doorsensor.domain.DoorState;
import dev.dada.doorsensor.repo.ConnectivityEventRepository;
import dev.dada.doorsensor.repo.DeviceRepository;
import dev.dada.doorsensor.repo.DoorEventRepository;
import dev.dada.doorsensor.web.dto.DeviceStatusDto;
import dev.dada.doorsensor.web.dto.EventDto;
import dev.dada.doorsensor.web.dto.HourlyCountDto;
import dev.dada.doorsensor.web.dto.StatsDto;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DoorController {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final DeviceRepository deviceRepo;
    private final DoorEventRepository eventRepo;
    private final ConnectivityEventRepository connRepo;
    private final RealtimeHandler realtime;

    public DoorController(DeviceRepository deviceRepo,
                          DoorEventRepository eventRepo,
                          ConnectivityEventRepository connRepo,
                          RealtimeHandler realtime) {
        this.deviceRepo = deviceRepo;
        this.eventRepo = eventRepo;
        this.connRepo = connRepo;
        this.realtime = realtime;
    }

    @GetMapping("/devices")
    public List<DeviceStatusDto> devices() {
        return deviceRepo.findAllByOrderByLabelAsc().stream()
                .map(DeviceStatusDto::from)
                .toList();
    }

    @GetMapping("/devices/{id}")
    public ResponseEntity<DeviceStatusDto> device(@PathVariable String id) {
        return deviceRepo.findById(id)
                .map(DeviceStatusDto::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/events")
    public List<EventDto> events(@RequestParam(required = false) String deviceId,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "50") int size) {
        int capped = Math.min(Math.max(size, 1), 200);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), capped);

        List<DoorEvent> content = (deviceId == null || deviceId.isBlank())
                ? eventRepo.findAllByOrderByOccurredAtDesc(pageable).getContent()
                : eventRepo.findByDeviceIdOrderByOccurredAtDesc(deviceId, pageable).getContent();

        return content.stream().map(EventDto::from).toList();
    }

    @GetMapping("/stats/hourly")
    public List<HourlyCountDto> hourly(@RequestParam(required = false) String deviceId) {
        Instant from = Instant.now().minusSeconds(24 * 3600).truncatedTo(java.time.temporal.ChronoUnit.HOURS);

        String filter = (deviceId == null) ? "" : deviceId;

        Map<Instant, Long> counts = eventRepo.countOpensByHour(from, filter).stream()
                .collect(Collectors.toMap(
                        row -> toInstant(row[0]),
                        row -> ((Number) row[1]).longValue()));

        List<HourlyCountDto> result = new ArrayList<>(24);
        Instant cursor = from;
        for (int i = 0; i < 24; i++) {
            result.add(new HourlyCountDto(cursor, counts.getOrDefault(cursor, 0L)));
            cursor = cursor.plusSeconds(3600);
        }
        return result;
    }

    @GetMapping("/stats")
    public StatsDto stats() {
        Instant startOfToday = LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant();
        Instant last24h = Instant.now().minusSeconds(24 * 3600);

        long opensToday = eventRepo.countByStateAndOccurredAtAfter(DoorState.OPEN, startOfToday);
        long opens24h = eventRepo.countByStateAndOccurredAtAfter(DoorState.OPEN, last24h);
        Double avgLag = eventRepo.averageLagMs(last24h);
        Long openMs = eventRepo.totalOpenMillis(last24h);
        long disconnects = connRepo.countByOnlineFalseAndReceivedAtAfter(last24h);

        return new StatsDto(
                opensToday,
                opens24h,
                avgLag == null ? 0 : avgLag,
                openMs == null ? 0 : openMs / 1000,
                disconnects
        );
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "devices", deviceRepo.count(),
                "events", eventRepo.count(),
                "dashboardsConnected", realtime.activeSessions()
        );
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Timestamp ts) {
            return ts.toInstant();
        }
        if (value instanceof java.time.OffsetDateTime odt) {
            return odt.toInstant();
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        throw new IllegalStateException("Kieu thoi gian khong ho tro: " + value.getClass());
    }
}
