package dev.dada.doorsensor.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BatteryLevelTest {

    @Test
    @DisplayName("Diem moc cua duong cong va hai dau")
    void diemMoc() {
        assertEquals(100, BatteryLevel.percent(4250));
        assertEquals(100, BatteryLevel.percent(4200));
        assertEquals(50, BatteryLevel.percent(3840));
        assertEquals(0, BatteryLevel.percent(3300));
        assertEquals(0, BatteryLevel.percent(3000));
        assertNull(BatteryLevel.percent(null));
    }

    @Test
    @DisplayName("Noi suy giua hai diem: 4150 mV (vua sac day, buoc 9) khoang 95 %")
    void noiSuy() {
        assertEquals(95, BatteryLevel.percent(4150));
        assertEquals(93, BatteryLevel.percent(4130));
    }

    @Test
    @DisplayName("3,75 V chi con khoang 25 %, khong phai 50 % nhu noi suy thang")
    void khongThang() {
        assertEquals(25, BatteryLevel.percent(3750));
    }

    @Test
    @DisplayName("Dien ap giam thi phan tram khong bao gio tang")
    void donDieu() {
        int last = 100;
        for (int mv = 4300; mv >= 3200; mv -= 5) {
            int p = BatteryLevel.percent(mv);
            assertTrue(p <= last, "tai " + mv);
            last = p;
        }
    }
}
