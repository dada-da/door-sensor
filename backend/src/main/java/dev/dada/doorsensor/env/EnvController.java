package dev.dada.doorsensor.env;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API doc so do moi truong dang tho.
 *
 * <p>Chi du de kiem tra du lieu dang ve truoc khi di. Gom nhom theo gio, ngay
 * cho trang thong ke (date_trunc) la viec cua nhom B, lam tu xa duoc.
 */
@RestController
@RequestMapping("/api/env")
public class EnvController {

    /** 7 ngay, nhip 60 giay thi khoang 10 nghin dong. Tren muc nay phai gom nhom. */
    private static final int MAX_HOURS = 24 * 7;
    private static final int MAX_ROWS = 12_000;

    private final SensorReadingRepository repo;

    public EnvController(SensorReadingRepository repo) {
        this.repo = repo;
    }

    public record ReadingDto(String deviceId, Instant measuredAt, Double temperatureC, Double humidityPct) {
        static ReadingDto from(SensorReading r) {
            return new ReadingDto(r.getDeviceId(), r.getMeasuredAt(), r.getTemperatureC(), r.getHumidityPct());
        }
    }

    public record LatestDto(String deviceId, Instant measuredAt, Instant receivedAt,
                            Double temperatureC, Double humidityPct,
                            long ageSeconds, long readingsLastHour) {
    }

    /** Ban ghi moi nhat moi thiet bi, kem tuoi va so ban ghi trong gio qua. */
    @GetMapping("/latest")
    public List<LatestDto> latest() {
        Instant now = Instant.now();
        Instant hourAgo = now.minus(Duration.ofHours(1));
        return repo.findLatestPerDevice().stream()
                .map(r -> new LatestDto(
                        r.getDeviceId(),
                        r.getMeasuredAt(),
                        r.getReceivedAt(),
                        r.getTemperatureC(),
                        r.getHumidityPct(),
                        Duration.between(r.getReceivedAt(), now).getSeconds(),
                        repo.countByDeviceIdAndReceivedAtAfter(r.getDeviceId(), hourAgo)))
                .toList();
    }

    @GetMapping("/readings")
    public List<ReadingDto> readings(@RequestParam(required = false) String deviceId,
                                     @RequestParam(defaultValue = "24") int hours) {
        int capped = Math.min(Math.max(hours, 1), MAX_HOURS);
        Instant from = Instant.now().minus(Duration.ofHours(capped));
        PageRequest limit = PageRequest.of(0, MAX_ROWS);

        List<SensorReading> rows = (deviceId == null || deviceId.isBlank())
                ? repo.findByMeasuredAtAfterOrderByMeasuredAtAsc(from, limit)
                : repo.findByDeviceIdAndMeasuredAtAfterOrderByMeasuredAtAsc(deviceId, from, limit);
        return rows.stream().map(ReadingDto::from).toList();
    }
}
