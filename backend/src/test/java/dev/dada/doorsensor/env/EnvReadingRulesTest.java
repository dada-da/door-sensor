package dev.dada.doorsensor.env;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EnvReadingRulesTest {

    private static final Instant NOW = Instant.parse("2026-10-12T03:00:00Z");

    @Test
    @DisplayName("So do binh thuong duoc nhan")
    void hopLe() {
        assertNull(EnvReadingRules.rejectReason(27.4, 63.2));
        assertNull(EnvReadingRules.rejectReason(27.4, null), "thieu do am van nhan");
        assertNull(EnvReadingRules.rejectReason(null, 63.2), "thieu nhiet do van nhan");
        assertNull(EnvReadingRules.rejectReason(-40.0, 0.0), "dung bien duoi");
        assertNull(EnvReadingRules.rejectReason(85.0, 100.0), "dung bien tren");
    }

    @Test
    @DisplayName("So ngoai dai do cua AHT20 hoac khong phai so bi loai")
    void biLoai() {
        assertNotNull(EnvReadingRules.rejectReason(null, null));
        assertNotNull(EnvReadingRules.rejectReason(85.1, 50.0));
        assertNotNull(EnvReadingRules.rejectReason(-40.1, 50.0));
        assertNotNull(EnvReadingRules.rejectReason(25.0, 100.1));
        assertNotNull(EnvReadingRules.rejectReason(25.0, -0.1));
        assertNotNull(EnvReadingRules.rejectReason(Double.NaN, 50.0));
        assertNotNull(EnvReadingRules.rejectReason(25.0, Double.POSITIVE_INFINITY));
    }

    @Test
    @DisplayName("Thieu ts hoac ts sai thi lay thoi diem nhan")
    void thoiDiemDo() {
        assertEquals(NOW, EnvReadingRules.measuredAt(null, NOW));
        assertEquals(NOW, EnvReadingRules.measuredAt(0L, NOW), "nam 1970: chua dong bo NTP");
        assertEquals(NOW, EnvReadingRules.measuredAt(NOW.plusSeconds(3 * 86400).getEpochSecond(), NOW));
        long tenSecondsAgo = NOW.minusSeconds(10).getEpochSecond();
        assertEquals(Instant.ofEpochSecond(tenSecondsAgo), EnvReadingRules.measuredAt(tenSecondsAgo, NOW));
    }

    @Test
    @DisplayName("Payload doc duoc ca ten truong ngan lan ten day du, bo qua truong la")
    void docPayload() throws Exception {
        ObjectMapper om = new ObjectMapper();
        EnvPayload a = om.readValue("{\"temp_c\":27.41,\"rh\":63.2,\"ts\":1760198400,\"rssi\":-61}", EnvPayload.class);
        assertEquals(Double.valueOf(27.41), a.temperatureC());
        assertEquals(Double.valueOf(63.2), a.humidityPct());
        assertEquals(Long.valueOf(1760198400L), a.ts());

        EnvPayload b = om.readValue("{\"temperature\":21.0,\"humidity\":40}", EnvPayload.class);
        assertEquals(Double.valueOf(21.0), b.temperatureC());
        assertEquals(Double.valueOf(40.0), b.humidityPct());
        assertNull(b.ts());
    }
}
