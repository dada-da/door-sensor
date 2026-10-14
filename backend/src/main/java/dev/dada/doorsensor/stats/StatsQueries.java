package dev.dada.doorsensor.stats;

/**
 * SQL cua trang thong ke. Moi phep gom nhom chay tren PostgreSQL, tra ve vai
 * tram dong thay vi hang chuc nghin ban ghi tho (bao cao IoT buoc 5 va 10).
 *
 * <p>De rieng thanh hang so de kiem thu duoc dung nguyen van cau SQL tren mot
 * PostgreSQL that (xem StatsQueriesSqlCheck trong ghi chu ban giao).
 *
 * <p>Gio dia phuong: moi phep cat theo gio/ngay/thu deu doi sang
 * Asia/Ho_Chi_Minh truoc. Cat theo UTC thi "7 gio sang" se roi vao cot 0 gio,
 * va heatmap lech 7 o ma khong bao loi gi.
 */
final class StatsQueries {

    private StatsQueries() {
    }

    static final String TZ = "Asia/Ho_Chi_Minh";

    /**
     * Nhiet do, do am theo tung khoang. date_bin (PostgreSQL 14+) chia deu theo
     * buoc tuy y (10 phut, 2 gio...), date_trunc chi cat duoc theo don vi lich.
     * Goc thoi gian 00:00 gio VN de cac moc chan dep tren truc.
     */
    static final String ENV_SERIES = """
            SELECT date_bin(CAST(:step AS interval), measured_at, make_timestamptz(2000, 1, 1, 0, 0, 0, 'Asia/Ho_Chi_Minh')) AS bucket,
                   AVG(temperature_c) AS temp_avg,
                   MIN(temperature_c) AS temp_min,
                   MAX(temperature_c) AS temp_max,
                   AVG(humidity_pct)  AS rh_avg,
                   MIN(humidity_pct)  AS rh_min,
                   MAX(humidity_pct)  AS rh_max,
                   COUNT(*)           AS n
            FROM sensor_reading
            WHERE measured_at >= :from
              AND (:deviceId = '' OR device_id = :deviceId)
            GROUP BY bucket
            ORDER BY bucket
            """;

    /** So lan mo theo gio hoac ngay (gio VN), tach tung cua. */
    static final String OPENS_SERIES = """
            SELECT (date_trunc(:unit, occurred_at AT TIME ZONE 'Asia/Ho_Chi_Minh')
                       AT TIME ZONE 'Asia/Ho_Chi_Minh') AS bucket,
                   device_id,
                   COUNT(*) AS opens
            FROM door_event
            WHERE state = 'OPEN'
              AND occurred_at >= :from
            GROUP BY bucket, device_id
            ORDER BY bucket, device_id
            """;

    /**
     * So lan mo theo thu trong tuan x gio trong ngay. isodow: 1 = thu Hai.
     * days_seen: so ngay moi thu thuc su nam trong khoang, de doi ra trung binh.
     */
    static final String HEATMAP = """
            SELECT EXTRACT(ISODOW FROM occurred_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::int AS dow,
                   EXTRACT(HOUR   FROM occurred_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::int AS hour,
                   COUNT(*) AS opens
            FROM door_event
            WHERE state = 'OPEN'
              AND occurred_at >= :from
              AND (:deviceId = '' OR device_id = :deviceId)
            GROUP BY dow, hour
            ORDER BY dow, hour
            """;

    /**
     * Thoi gian o tung trang thai trong khoang [from, now], tung cua.
     *
     * <p>Moi su kien la dau mot doan; doan keo dai toi su kien ke tiep (LEAD).
     * Doan bat dau truoc :from duoc cat lai tai :from, nen trang thai dau khoang
     * van dung du su kien gan nhat xay ra tu tuan truoc. Doan cuoi keo toi hien tai.
     *
     * <p>Han che, ghi vao bao cao: luc thiet bi mat lien lac van tinh la trang
     * thai cuoi cung da biet.
     */
    static final String STATE_DURATION = """
            WITH ev AS (
                SELECT device_id, state, occurred_at,
                       LEAD(occurred_at) OVER (PARTITION BY device_id ORDER BY occurred_at, id) AS next_at
                FROM door_event
                WHERE occurred_at < now()
            ),
            seg AS (
                SELECT device_id, state,
                       GREATEST(occurred_at, :from) AS seg_start,
                       LEAST(COALESCE(next_at, now()), now()) AS seg_end
                FROM ev
                WHERE COALESCE(next_at, now()) > :from
            )
            SELECT device_id, state,
                   CAST(SUM(EXTRACT(EPOCH FROM (seg_end - seg_start))) AS DOUBLE PRECISION) AS seconds
            FROM seg
            WHERE seg_end > seg_start
            GROUP BY device_id, state
            ORDER BY device_id, state
            """;

    /**
     * Pin va thoi gian thuc theo khoang, tu device_telemetry (moi lan thuc mot dong).
     * Pin chi tinh tren dong co battery_mv; work_ms chi co nghia khi firmware tat
     * DEBUG_SERIAL, nhung loc o phia doc so lieu, khong loc o day.
     */
    static final String DEVICE_SERIES = """
            SELECT date_bin(CAST(:step AS interval), received_at, make_timestamptz(2000, 1, 1, 0, 0, 0, 'Asia/Ho_Chi_Minh')) AS bucket,
                   CAST(AVG(battery_mv) AS DOUBLE PRECISION) AS batt_avg,
                   MIN(battery_mv) AS batt_min,
                   MAX(battery_mv) AS batt_max,
                   CAST(AVG(work_ms) AS DOUBLE PRECISION) AS work_avg,
                   CAST(AVG(rssi) AS DOUBLE PRECISION) AS rssi_avg,
                   COUNT(*)        AS wakes
            FROM device_telemetry
            WHERE device_id = :deviceId
              AND received_at >= :from
            GROUP BY bucket
            ORDER BY bucket
            """;

    /** Tong ket cho thu nghiem tang toc: lan thuc dau, cuoi, so lan, thoi gian thuc. */
    static final String DEVICE_SUMMARY = """
            SELECT MIN(received_at) AS first_at,
                   MAX(received_at) AS last_at,
                   COUNT(*)         AS wakes,
                   CAST(AVG(work_ms) AS DOUBLE PRECISION) AS work_avg,
                   percentile_cont(0.5)  WITHIN GROUP (ORDER BY work_ms) AS work_p50,
                   percentile_cont(0.95) WITHIN GROUP (ORDER BY work_ms) AS work_p95,
                   (SELECT battery_mv FROM device_telemetry t2
                     WHERE t2.device_id = :deviceId AND t2.received_at >= :from AND t2.battery_mv IS NOT NULL
                     ORDER BY t2.received_at DESC LIMIT 1) AS batt_last
            FROM device_telemetry
            WHERE device_id = :deviceId
              AND received_at >= :from
            """;
}
