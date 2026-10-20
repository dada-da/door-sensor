package dev.dada.doorsensor.env;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dada.doorsensor.env.EnvAlertEvaluator.Direction;
import dev.dada.doorsensor.env.EnvAlertEvaluator.Limits;
import dev.dada.doorsensor.env.EnvAlertEvaluator.Metric;
import dev.dada.doorsensor.env.EnvAlertEvaluator.Transition;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** May trang thai canh bao nguong, voi thoi gian gia moi buoc 60 giay (nhip node coi). */
class EnvAlertEvaluatorTest {

    private static final Instant T0 = Instant.parse("2026-10-20T07:00:00Z");
    private static final Duration HOLD = Duration.ofMinutes(5);
    private static final Limits TEMP = new Limits(35.0, 12.0);

    private final EnvAlertEvaluator ev = new EnvAlertEvaluator();

    private Optional<Transition> temp(int minute, double v) {
        return ev.evaluate("siren-01", Metric.TEMP, v, TEMP, HOLD, T0.plusSeconds(60L * minute));
    }

    @Test
    @DisplayName("Vuot nguong lien tuc du 5 phut moi bao, va bao dung mot lan")
    void baoSauThoiGianGiu() {
        for (int m = 0; m < 5; m++) {
            assertTrue(temp(m, 36).isEmpty(), "chua du 5 phut o phut " + m);
        }
        Transition t = temp(5, 36.4).orElseThrow();
        assertTrue(t.raised());
        assertEquals(Direction.HIGH, t.direction());
        assertEquals(35.0, t.threshold());
        assertEquals(T0, t.since());
        for (int m = 6; m < 30; m++) {
            assertTrue(temp(m, 37).isEmpty(), "khong bao lap");
        }
        assertEquals(1, ev.active().size());
    }

    @Test
    @DisplayName("Mot mau nhieu roi ve ngay: khong bao")
    void mauNhieu() {
        assertTrue(temp(0, 40).isEmpty());
        assertTrue(temp(1, 30).isEmpty());
        for (int m = 2; m < 7; m++) {
            assertTrue(temp(m, 36).isEmpty(), "dem lai tu dau sau khi ve, phut " + m);
        }
        assertTrue(temp(7, 36).isPresent(), "5 phut tinh tu phut 2");
    }

    @Test
    @DisplayName("Khoang tre: lang vang quanh 35 khong ban tin het, phai xuong 34,5")
    void khoangTre() {
        for (int m = 0; m <= 5; m++) temp(m, 36);
        assertTrue(temp(6, 34.9).isEmpty());
        assertTrue(temp(7, 35.2).isEmpty());
        assertTrue(temp(8, 34.6).isEmpty());
        Transition t = temp(9, 34.5).orElseThrow();
        assertFalse(t.raised());
        assertEquals(36.0, t.peak());
        assertTrue(ev.active().isEmpty());
    }

    @Test
    @DisplayName("Gia tri dinh cua ca dot duoc giu de bao luc het")
    void dinh() {
        for (int m = 0; m <= 5; m++) temp(m, 35.5 + m * 0.5);
        temp(6, 39.1);
        temp(7, 36);
        assertEquals(39.1, temp(8, 30).orElseThrow().peak());
    }

    @Test
    @DisplayName("Nguong duoi: lanh qua thi bao LOW, am len qua 12,5 thi het")
    void nguongDuoi() {
        for (int m = 0; m < 5; m++) temp(m, 10);
        Transition t = temp(5, 10).orElseThrow();
        assertEquals(Direction.LOW, t.direction());
        assertTrue(temp(6, 12.3).isEmpty());
        assertFalse(temp(7, 12.5).orElseThrow().raised());
    }

    @Test
    @DisplayName("Thoi gian giu 0: bao ngay mau dau tien")
    void giuBang0() {
        Optional<Transition> t = ev.evaluate("x", Metric.RH, 90.0, new Limits(85.0, null), Duration.ZERO, T0);
        assertTrue(t.orElseThrow().raised());
    }

    @Test
    @DisplayName("Bo nguong tren giao dien khi dang bao: coi nhu het")
    void boNguong() {
        for (int m = 0; m <= 5; m++) temp(m, 36);
        Optional<Transition> t = ev.evaluate("siren-01", Metric.TEMP, 36.0, new Limits(null, 12.0), HOLD,
                T0.plusSeconds(600));
        assertFalse(t.orElseThrow().raised());
        assertEquals(35.0, t.get().threshold(), "van ghi nguong cu de nhat ky co nghia");
    }

    @Test
    @DisplayName("Hai dai luong, hai thiet bi doc lap nhau")
    void docLap() {
        for (int m = 0; m <= 5; m++) {
            temp(m, 36);
            ev.evaluate("siren-01", Metric.RH, 60.0, new Limits(85.0, 30.0), HOLD, T0.plusSeconds(60L * m));
            ev.evaluate("siren-02", Metric.TEMP, 25.0, TEMP, HOLD, T0.plusSeconds(60L * m));
        }
        assertEquals(1, ev.active().size());
        assertEquals(Metric.TEMP, ev.active().get(0).metric());
    }

    @Test
    @DisplayName("Thieu so do (null) khong lam doi trang thai")
    void thieuSo() {
        for (int m = 0; m <= 5; m++) temp(m, 36);
        assertTrue(ev.evaluate("siren-01", Metric.TEMP, null, TEMP, HOLD, T0.plusSeconds(400)).isEmpty());
        assertEquals(1, ev.active().size());
    }

    @Test
    @DisplayName("Khoi phuc sau khoi dong lai: khong bao vuot lan hai, van bao het")
    void khoiPhuc() {
        ev.restore("siren-01", Metric.TEMP, Direction.HIGH, T0, 36.0, 35.0);
        assertTrue(temp(1, 37).isEmpty());
        assertFalse(temp(2, 33).orElseThrow().raised());
    }

    @Test
    @DisplayName("Tat canh bao: quen trang thai, khong bao het")
    void reset() {
        for (int m = 0; m <= 5; m++) temp(m, 36);
        ev.reset();
        assertTrue(ev.active().isEmpty());
        assertTrue(temp(6, 20).isEmpty());
    }
}
