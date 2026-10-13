package dev.dada.doorsensor.env;

import java.time.Duration;
import java.time.Instant;

/**
 * Luat kiem tra so do moi truong. Logic thuan, khong phu thuoc Spring.
 *
 * <p>Gioi han lay theo dai do cua AHT20: nhiet do -40 den 85 do C, do am 0 den
 * 100%. So nam ngoai dai nay khong phai thoi tiet bat thuong ma la loi doc cam
 * bien (day long, I2C nhieu, chip chua san sang). Ghi vao CSDL thi do thi va moi
 * phep thong ke phia sau deu sai, nen loai ngay o cua vao.
 */
public final class EnvReadingRules {

    public static final double MIN_TEMP_C = -40.0;
    public static final double MAX_TEMP_C = 85.0;
    public static final double MIN_RH = 0.0;
    public static final double MAX_RH = 100.0;

    /** Giong DoorService: dong ho thiet bi chua dong bo NTP se bao nam 1970. */
    static final Instant CLOCK_FLOOR = Instant.parse("2020-01-01T00:00:00Z");
    static final Duration CLOCK_CEILING_SKEW = Duration.ofDays(1);

    private EnvReadingRules() {
    }

    /** Ly do loai, hoac null neu hop le. Can it nhat mot trong hai dai luong. */
    public static String rejectReason(Double temperatureC, Double humidityPct) {
        if (temperatureC == null && humidityPct == null) {
            return "thieu ca nhiet do lan do am";
        }
        if (temperatureC != null) {
            if (temperatureC.isNaN() || temperatureC.isInfinite()) {
                return "nhiet do khong phai so";
            }
            if (temperatureC < MIN_TEMP_C || temperatureC > MAX_TEMP_C) {
                return "nhiet do " + temperatureC + " ngoai dai do cua AHT20";
            }
        }
        if (humidityPct != null) {
            if (humidityPct.isNaN() || humidityPct.isInfinite()) {
                return "do am khong phai so";
            }
            if (humidityPct < MIN_RH || humidityPct > MAX_RH) {
                return "do am " + humidityPct + " ngoai 0-100%";
            }
        }
        return null;
    }

    /**
     * Thoi diem do. Tin {@code ts} cua thiet bi (giay epoch) neu hop ly, nguoc
     * lai lay thoi diem nhan. Node coi khong bat buoc co NTP, nen thieu ts la
     * truong hop binh thuong chu khong phai loi.
     */
    public static Instant measuredAt(Long deviceTs, Instant receivedAt) {
        if (deviceTs == null) {
            return receivedAt;
        }
        Instant candidate = Instant.ofEpochSecond(deviceTs);
        if (candidate.isBefore(CLOCK_FLOOR) || candidate.isAfter(receivedAt.plus(CLOCK_CEILING_SKEW))) {
            return receivedAt;
        }
        return candidate;
    }
}
