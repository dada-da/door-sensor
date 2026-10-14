package dev.dada.doorsensor.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

class DeviceTelemetryIngestTest {

    private final ObjectMapper om = new ObjectMapper();
    private final Instant now = Instant.parse("2026-10-15T01:00:00Z");

    @Test
    void docDuTinDoor02() throws Exception {
        String json = """
                {"device_id":"door-02","label":"Cua tang 2","state":"CLOSED","seq":11,"rssi":-61,
                 "boot_count":1234,"ts":1792026000,"wake":"timer","work_ms":912,"wifi_ms":540,
                 "mqtt_ms":85,"last_rtt_ms":31,"battery_mv":4012,"batt_adc_mv":2006}""";
        TelemetryPayload p = om.readValue(json, TelemetryPayload.class);
        MapSqlParameterSource s = DeviceTelemetryIngest.params("door-02", p, now);

        assertEquals("door-02", s.getValue("deviceId"));
        assertEquals("CLOSED", s.getValue("state"));
        assertEquals("timer", s.getValue("wake"));
        assertEquals(4012, s.getValue("batteryMv"));
        assertEquals(2006, s.getValue("battAdcMv"));
        assertEquals(-61, s.getValue("rssi"));
        assertEquals(1234L, s.getValue("bootCount"));
        assertEquals(912, s.getValue("workMs"));
        assertEquals(540, s.getValue("wifiMs"));
        assertEquals(85, s.getValue("mqttMs"));
        assertEquals(31, s.getValue("rttMs"));
        assertEquals(Timestamp.from(Instant.ofEpochSecond(1792026000L)), s.getValue("deviceTs"));
        assertEquals(Timestamp.from(now), s.getValue("receivedAt"));
    }

    @Test
    void tinDoor01ThieuTruongDoDacThiGhiNull() throws Exception {
        TelemetryPayload p = om.readValue("{\"state\":\"OPEN\",\"rssi\":-48}", TelemetryPayload.class);
        MapSqlParameterSource s = DeviceTelemetryIngest.params("door-01", p, now);
        assertEquals("OPEN", s.getValue("state"));
        assertNull(s.getValue("batteryMv"));
        assertNull(s.getValue("workMs"));
        assertNull(s.getValue("deviceTs"));
    }

    @Test
    void dongHoChuaDongBoThiKhongGhiMocSai() {
        assertNull(DeviceTelemetryIngest.deviceTs(12L, now));                          // nam 1970
        assertNull(DeviceTelemetryIngest.deviceTs(now.getEpochSecond() + 3 * 86400L, now)); // tuong lai
        assertEquals(Timestamp.from(Instant.ofEpochSecond(now.getEpochSecond() - 5)),
                DeviceTelemetryIngest.deviceTs(now.getEpochSecond() - 5, now));
    }

    @Test
    void wakeDaiQuaThiCat() throws Exception {
        TelemetryPayload p = om.readValue("{\"wake\":\"mot-ly-do-rat-dai-qua-muc\"}", TelemetryPayload.class);
        assertEquals(16, ((String) DeviceTelemetryIngest.params("x", p, now).getValue("wake")).length());
    }
}
