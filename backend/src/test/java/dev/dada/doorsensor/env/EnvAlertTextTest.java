package dev.dada.doorsensor.env;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dada.doorsensor.env.EnvAlertEvaluator.Direction;
import dev.dada.doorsensor.env.EnvAlertEvaluator.Metric;
import dev.dada.doorsensor.env.EnvAlertEvaluator.Transition;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Noi dung tin Telegram canh bao, /nhietdo va kiem tra nguong tu giao dien. */
class EnvAlertTextTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant SINCE = Instant.parse("2026-10-20T07:02:00Z"); // 14:02 gio VN

    @Test
    @DisplayName("Tin vuot nguong: gia tri, nguong, gio bat dau, dau phay thap phan")
    void tinVuot() {
        Transition t = new Transition("siren-01", Metric.TEMP, Direction.HIGH, true, 36.24, 35.0,
                SINCE.plusSeconds(300), SINCE, 36.24);
        String s = EnvAlertService.message(t, VN);
        assertTrue(s.contains("Nhiệt độ cao: 36,2 °C"), s);
        assertTrue(s.contains("trên ngưỡng 35,0 °C"), s);
        assertTrue(s.contains("từ 14:02"), s);
    }

    @Test
    @DisplayName("Tin het: gia tri dinh va thoi gian keo dai")
    void tinHet() {
        Transition t = new Transition("siren-01", Metric.RH, Direction.HIGH, false, 80.0, 85.0,
                SINCE.plusSeconds(72 * 60), SINCE, 93.4);
        String s = EnvAlertService.message(t, VN);
        assertTrue(s.contains("Độ ẩm đã về bình thường: 80,0 %"), s);
        assertTrue(s.contains("Cao nhất 93,4 %"), s);
        assertTrue(s.contains("1 giờ 12 phút"), s);
    }

    @Test
    @DisplayName("/nhietdo: so do, khoang 24 gio, nguong, canh bao dang mo")
    void nhietDo() {
        EnvAlertRule rule = new EnvAlertRule();
        rule.setTempHigh(35.0);
        rule.setTempLow(12.0);
        rule.setRhHigh(85.0);
        Instant now = SINCE.plusSeconds(600);
        String s = EnvReport.format(
                List.of(new EnvReport.Line("siren-01", now.minusSeconds(30), 27.41, 63.2, 25.1, 29.0, 55.0, 70.4)),
                rule,
                List.of(new EnvAlertEvaluator.Active("siren-01", Metric.RH, Direction.HIGH, SINCE, 88.0, 85.0, 90.0)),
                now, VN);
        assertTrue(s.contains("27,4 °C"), s);
        assertTrue(s.contains("63,2 %"), s);
        assertTrue(s.contains("25,1–29,0 °C"), s);
        assertTrue(s.contains("nhiệt độ ngoài 12,0–35,0 °C"), s);
        assertTrue(s.contains("độ ẩm trên 85,0 %"), s);
        assertTrue(s.contains("Độ ẩm cao</b> từ 14:02"), s);
        assertFalse(s.contains("đã cũ"), s);
    }

    @Test
    @DisplayName("/nhietdo: so do qua 5 phut thi ghi ro da cu")
    void soCu() {
        Instant now = SINCE.plusSeconds(3600);
        String s = EnvReport.format(List.of(new EnvReport.Line("siren-01", SINCE, 27.0, 60.0,
                null, null, null, null)), new EnvAlertRule(), List.of(), now, VN);
        assertTrue(s.contains("đã cũ 1 giờ"), s);
    }

    @Test
    @DisplayName("Nguong thap phai nho hon nguong cao it nhat hai lan khoang tre")
    void kiemNguong() {
        assertNull(EnvAlertController.problem(new EnvAlertController.RuleDto(true, 35.0, 12.0, 85.0, 30.0, 5)));
        assertNull(EnvAlertController.problem(new EnvAlertController.RuleDto(true, null, 12.0, null, null, 5)));
        assertNotNull(EnvAlertController.problem(new EnvAlertController.RuleDto(true, 30.0, 29.5, null, null, 5)));
        assertNotNull(EnvAlertController.problem(new EnvAlertController.RuleDto(true, null, null, 60.0, 58.0, 5)));
    }

    @Test
    @DisplayName("So mot chu so thap phan, dau phay")
    void dinhDangSo() {
        assertEquals("27,4", EnvAlertService.num(27.41));
        assertEquals("--", EnvAlertService.num(null));
    }
}
