package dev.dada.doorsensor.stats;

import dev.dada.doorsensor.domain.Device;
import dev.dada.doorsensor.repo.DeviceRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API cho trang Thong ke (mon IoT). Moi phep tinh gom nhom chay o PostgreSQL.
 *
 * <pre>
 * GET /api/stats/env?hours=24&deviceId=siren-01    duong nhiet do, do am (tb, min, max)
 * GET /api/stats/opens?days=7&unit=day              cot so lan mo, tach tung cua
 * GET /api/stats/heatmap?days=28&deviceId=          o so lan mo theo thu x gio
 * GET /api/stats/state-share?days=7                 tron ti le thoi gian mo/dong
 * </pre>
 *
 * Khong dung chung DoorController: DoorController thuoc phan co tu truoc T0,
 * cac API o day chi doc, them moi hoan toan.
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private static final ZoneId ZONE = ZoneId.of(StatsQueries.TZ);

    private final NamedParameterJdbcTemplate jdbc;
    private final DeviceRepository devices;

    public StatsController(NamedParameterJdbcTemplate jdbc, DeviceRepository devices) {
        this.jdbc = jdbc;
        this.devices = devices;
    }

    // ------------------------------------------------------------------ DTO

    public record EnvPoint(Instant bucket, Double tempAvg, Double tempMin, Double tempMax,
                           Double rhAvg, Double rhMin, Double rhMax, long n) {
    }

    public record EnvSeries(int hours, String step, List<EnvPoint> points) {
    }

    public record DoorRef(String id, String label) {
    }

    /** {@code counts.get(i)} la so lan mo cua tung cua tai {@code buckets.get(i)}, cung thu tu {@code doors}. */
    public record OpensSeries(String unit, List<DoorRef> doors, List<Instant> buckets, List<List<Long>> counts) {
    }

    /** {@code cells[dow-1][hour]}: so lan mo. {@code daysPerDow[dow-1]}: so ngay thu do nam trong khoang. */
    public record Heatmap(int days, long[][] cells, int[] daysPerDow, long max) {
    }

    public record StateShare(String deviceId, String label, double openSeconds, double closedSeconds) {
    }

    // ------------------------------------------------------------------ API

    @GetMapping("/env")
    public EnvSeries env(@RequestParam(defaultValue = "24") int hours,
                         @RequestParam(defaultValue = "") String deviceId) {
        int h = Math.min(Math.max(hours, 1), 24 * 31);
        // Moi do thi tu khoang 80 toi 150 diem (24 gio: 144, 7 ngay: 84, 30 ngay: 120):
        // du min cho duong, khong nang trinh duyet.
        String step = h <= 24 ? "10 minutes" : h <= 72 ? "30 minutes" : h <= 24 * 14 ? "2 hours" : "6 hours";

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("step", step)
                .addValue("from", Timestamp.from(Instant.now().minus(Duration.ofHours(h))))
                .addValue("deviceId", deviceId);

        List<EnvPoint> points = jdbc.query(StatsQueries.ENV_SERIES, p, (rs, i) -> new EnvPoint(
                rs.getTimestamp("bucket").toInstant(),
                round(rs.getObject("temp_avg", Double.class), 2),
                round(rs.getObject("temp_min", Double.class), 2),
                round(rs.getObject("temp_max", Double.class), 2),
                round(rs.getObject("rh_avg", Double.class), 1),
                round(rs.getObject("rh_min", Double.class), 1),
                round(rs.getObject("rh_max", Double.class), 1),
                rs.getLong("n")));
        return new EnvSeries(h, step, points);
    }

    @GetMapping("/opens")
    public OpensSeries opens(@RequestParam(defaultValue = "7") int days,
                             @RequestParam(defaultValue = "day") String unit) {
        String u = "hour".equals(unit) ? "hour" : "day";
        int d = Math.min(Math.max(days, 1), "hour".equals(u) ? 7 : 90);

        // Moc dau: dau gio/dau ngay theo gio VN, de cot dau tien khong bi cat do.
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        ZonedDateTime start = "hour".equals(u)
                ? now.minusDays(d).truncatedTo(ChronoUnit.HOURS).plusHours(1)
                : now.minusDays(d - 1L).truncatedTo(ChronoUnit.DAYS);

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("unit", u)
                .addValue("from", Timestamp.from(start.toInstant()));

        Map<Instant, Map<String, Long>> rows = new HashMap<>();
        jdbc.query(StatsQueries.OPENS_SERIES, p, rs -> {
            rows.computeIfAbsent(rs.getTimestamp("bucket").toInstant(), k -> new HashMap<>())
                    .put(rs.getString("device_id"), rs.getLong("opens"));
        });

        List<DoorRef> doors = doorRefs();
        List<Instant> buckets = new ArrayList<>();
        List<List<Long>> counts = new ArrayList<>();
        // Dien du moi khoang ke ca khoang khong co lan mo nao: cot 0 cung la du lieu.
        for (ZonedDateTime b = start; !b.isAfter(now); b = "hour".equals(u) ? b.plusHours(1) : b.plusDays(1)) {
            Instant key = b.toInstant();
            Map<String, Long> row = rows.getOrDefault(key, Map.of());
            buckets.add(key);
            counts.add(doors.stream().map(dr -> row.getOrDefault(dr.id(), 0L)).toList());
        }
        return new OpensSeries(u, doors, buckets, counts);
    }

    @GetMapping("/heatmap")
    public Heatmap heatmap(@RequestParam(defaultValue = "28") int days,
                           @RequestParam(defaultValue = "") String deviceId) {
        int d = Math.min(Math.max(days, 7), 180);
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        ZonedDateTime start = now.minusDays(d - 1L).truncatedTo(ChronoUnit.DAYS);

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("from", Timestamp.from(start.toInstant()))
                .addValue("deviceId", deviceId);

        long[][] cells = new long[7][24];
        long[] max = {0};
        jdbc.query(StatsQueries.HEATMAP, p, rs -> {
            long v = rs.getLong("opens");
            cells[rs.getInt("dow") - 1][rs.getInt("hour")] = v;
            max[0] = Math.max(max[0], v);
        });

        // So ngay moi thu trong khoang: 28 ngay thi moi thu 4 lan, 30 ngay thi lech nhau.
        int[] perDow = new int[7];
        for (ZonedDateTime day = start; !day.isAfter(now); day = day.plusDays(1)) {
            perDow[day.getDayOfWeek().getValue() - 1]++;
        }
        return new Heatmap(d, cells, perDow, max[0]);
    }

    @GetMapping("/state-share")
    public List<StateShare> stateShare(@RequestParam(defaultValue = "7") int days) {
        int d = Math.min(Math.max(days, 1), 90);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("from", Timestamp.from(Instant.now().minus(Duration.ofDays(d))));

        Map<String, double[]> acc = new LinkedHashMap<>();
        jdbc.query(StatsQueries.STATE_DURATION, p, rs -> {
            double[] v = acc.computeIfAbsent(rs.getString("device_id"), k -> new double[2]);
            v["OPEN".equals(rs.getString("state")) ? 0 : 1] += rs.getDouble("seconds");
        });

        Map<String, String> labels = new HashMap<>();
        for (Device dev : devices.findAllByOrderByLabelAsc()) labels.put(dev.getId(), dev.getLabel());
        return acc.entrySet().stream()
                .map(e -> new StateShare(e.getKey(), labels.getOrDefault(e.getKey(), e.getKey()),
                        Math.round(e.getValue()[0]), Math.round(e.getValue()[1])))
                .toList();
    }

    // ------------------------------------------------------------------

    private List<DoorRef> doorRefs() {
        return devices.findAllByOrderByLabelAsc().stream()
                .map(dev -> new DoorRef(dev.getId(), dev.getLabel()))
                .toList();
    }

    private static Double round(Double v, int digits) {
        if (v == null) return null;
        double f = Math.pow(10, digits);
        return Math.round(v * f) / f;
    }
}
