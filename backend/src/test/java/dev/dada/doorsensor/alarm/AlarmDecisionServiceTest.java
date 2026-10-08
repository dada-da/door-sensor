package dev.dada.doorsensor.alarm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bon truong hop bien trong bang 3.4 cua bao cao, cong tran cung va thao tac tay.
 * Dung dong ho co dinh nen kiem thu lap lai duoc, khong phu thuoc gio chay.
 */
class AlarmDecisionServiceTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    /** 2026-10-05 la thu Hai. */
    private AlarmDecisionService serviceAt(int hour, int minute) {
        Instant fixed = ZonedDateTime.of(
                LocalDate.of(2026, 10, 5), LocalTime.of(hour, minute), VN).toInstant();
        return new AlarmDecisionService(Clock.fixed(fixed, VN));
    }

    private AlarmSchedule schedule() {
        AlarmSchedule s = new AlarmSchedule();
        s.setEnabled(true);
        s.setStartTime(LocalTime.of(0, 0));
        s.setEndTime(LocalTime.of(7, 0));
        s.setSirenSeconds(30);
        return s;
    }

    private DoorEvent moCua() {
        return new DoorEvent("door-02", DoorState.OPEN, Instant.now(), true);
    }

    @Test
    @DisplayName("Cua mo trong khung gio bao ve thi coi keu")
    void moTrongKhung() {
        AlarmDecision d = serviceAt(3, 0).decide(moCua(), schedule(), false);
        assertTrue(d.shouldBeep());
        assertEquals(30_000L, d.durationMs());
        assertEquals("DOOR_OPEN", d.reason());
    }

    @Test
    @DisplayName("Cua mo ngoai khung gio thi khong keu")
    void moNgoaiKhung() {
        AlarmDecision d = serviceAt(12, 0).decide(moCua(), schedule(), false);
        assertFalse(d.shouldBeep());
        assertEquals("NOT_ARMED", d.reason());
    }

    @Test
    @DisplayName("Dong cua khong bao gio kich hoat coi")
    void dongCua() {
        DoorEvent dong = new DoorEvent("door-02", DoorState.CLOSED, Instant.now(), true);
        AlarmDecision d = serviceAt(3, 0).decide(dong, schedule(), false);
        assertFalse(d.shouldBeep());
    }

    @Test
    @DisplayName("Cua dang mo san luc khung gio bat dau thi khong keu")
    void moSanLucBatDau() {
        DoorEvent quet = new DoorEvent("door-02", DoorState.OPEN, Instant.now(), false);
        AlarmDecision d = serviceAt(0, 0).decide(quet, schedule(), false);
        assertFalse(d.shouldBeep());
        assertEquals("ALREADY_OPEN_AT_ARM_TIME", d.reason());
    }

    @Test
    @DisplayName("Cua mo tiep trong luc coi dang keu thi gia han")
    void giaHan() {
        AlarmDecision d = serviceAt(3, 0).decide(moCua(), schedule(), true);
        assertTrue(d.shouldBeep());
        assertEquals("DOOR_OPEN_EXTEND", d.reason());
    }

    @Test
    @DisplayName("Thoi luong vuot tran bi cat ve 120 giay")
    void tranCung() {
        AlarmSchedule s = schedule();
        s.setSirenSeconds(600);   // gia su lot qua rang buoc CSDL
        AlarmDecision d = serviceAt(3, 0).decide(moCua(), s, false);
        assertEquals(120_000L, d.durationMs());
    }

    @Test
    @DisplayName("Tat bao ve thu cong thi khong keu du dang trong khung gio")
    void tatThuCong() {
        AlarmSchedule s = schedule();
        s.setOverrideMode(AlarmSchedule.Override.DISARMED);
        s.setOverrideUntil(Instant.parse("2026-10-05T23:59:00Z"));

        AlarmDecisionService svc = serviceAt(3, 0);
        assertFalse(svc.isArmed(s));
        assertFalse(svc.decide(moCua(), s, false).shouldBeep());
    }

    @Test
    @DisplayName("Thao tac tay het han thi quay ve theo lich")
    void thaoTacTayHetHan() {
        AlarmSchedule s = schedule();
        s.setOverrideMode(AlarmSchedule.Override.DISARMED);
        s.setOverrideUntil(Instant.parse("2026-10-04T00:00:00Z"));   // da qua

        assertTrue(serviceAt(3, 0).isArmed(s));
    }

    @Test
    @DisplayName("Bat bao ve thu cong thi keu du dang ngoai khung gio")
    void batThuCong() {
        AlarmSchedule s = schedule();
        s.setOverrideMode(AlarmSchedule.Override.ARMED);
        s.setOverrideUntil(Instant.parse("2026-10-05T23:59:00Z"));

        assertTrue(serviceAt(12, 0).decide(moCua(), s, false).shouldBeep());
    }
}
