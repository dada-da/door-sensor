package dev.dada.doorsensor.alarm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sau truong hop bien trong bang 5.11 cua bao cao, cong cac truong hop ngay
 * trong tuan. Viet kiem thu TRUOC khi viet ham, vi loi vat qua nua dem hong
 * im lang chu khong nem ngoai le.
 */
class ScheduleWindowTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<DayOfWeek> MOI_NGAY = EnumSet.allOf(DayOfWeek.class);

    /** 2026-10-05 la thu Hai, 2026-10-06 la thu Ba. */
    private ZonedDateTime luc(int day, int hour, int minute) {
        return ZonedDateTime.of(LocalDate.of(2026, 10, day), LocalTime.of(hour, minute), VN);
    }

    private ScheduleWindow khung(String start, String end) {
        return new ScheduleWindow(true, LocalTime.parse(start), LocalTime.parse(end), MOI_NGAY);
    }

    @Nested
    @DisplayName("Khung khong vat qua nua dem")
    class KhongVat {

        @Test
        @DisplayName("03:00 nam trong khung 00:00-07:00")
        void trongKhung() {
            assertTrue(khung("00:00", "07:00").contains(luc(5, 3, 0)));
        }

        @Test
        @DisplayName("12:00 nam ngoai khung 00:00-07:00")
        void ngoaiKhung() {
            assertFalse(khung("00:00", "07:00").contains(luc(5, 12, 0)));
        }

        @Test
        @DisplayName("Dung gio bat dau thi tinh la trong khung")
        void bienDuoi() {
            assertTrue(khung("00:00", "07:00").contains(luc(5, 0, 0)));
        }

        @Test
        @DisplayName("Dung gio ket thuc thi tinh la ngoai khung")
        void bienTren() {
            assertFalse(khung("00:00", "07:00").contains(luc(5, 7, 0)));
        }
    }

    @Nested
    @DisplayName("Khung vat qua nua dem")
    class Vat {

        @Test
        @DisplayName("23:00 nam trong khung 22:00-06:00, phia truoc nua dem")
        void truocNuaDem() {
            assertTrue(khung("22:00", "06:00").contains(luc(5, 23, 0)));
        }

        @Test
        @DisplayName("02:00 nam trong khung 22:00-06:00, phia sau nua dem")
        void sauNuaDem() {
            assertTrue(khung("22:00", "06:00").contains(luc(6, 2, 0)));
        }

        @Test
        @DisplayName("12:00 nam ngoai khung 22:00-06:00")
        void ngoaiKhung() {
            assertFalse(khung("22:00", "06:00").contains(luc(5, 12, 0)));
        }
    }

    @Test
    @DisplayName("Khung rong 08:00-08:00 luon tra ve false")
    void khungRong() {
        assertFalse(khung("08:00", "08:00").contains(luc(5, 8, 0)));
        assertFalse(khung("08:00", "08:00").contains(luc(5, 20, 0)));
    }

    @Test
    @DisplayName("Tat enabled thi luon tra ve false")
    void tatHan() {
        ScheduleWindow w = new ScheduleWindow(
                false, LocalTime.of(0, 0), LocalTime.of(7, 0), MOI_NGAY);
        assertFalse(w.contains(luc(5, 3, 0)));
    }

    @Nested
    @DisplayName("Loc theo ngay trong tuan")
    class Ngay {

        @Test
        @DisplayName("Khung chi bat thu Hai thi thu Ba khong tinh")
        void locNgay() {
            ScheduleWindow w = new ScheduleWindow(
                    true, LocalTime.of(0, 0), LocalTime.of(7, 0), EnumSet.of(DayOfWeek.MONDAY));
            assertTrue(w.contains(luc(5, 3, 0)));    // thu Hai
            assertFalse(w.contains(luc(6, 3, 0)));   // thu Ba
        }

        @Test
        @DisplayName("Khung vat qua nua dem thuoc ve ngay no bat dau")
        void vatQuaThuocNgayBatDau() {
            // Chi bat thu Hai. 02:00 sang thu Ba van thuoc khung bat dau 22:00 thu Hai.
            ScheduleWindow w = new ScheduleWindow(
                    true, LocalTime.of(22, 0), LocalTime.of(6, 0), EnumSet.of(DayOfWeek.MONDAY));
            assertTrue(w.contains(luc(5, 23, 0)));   // 23:00 thu Hai
            assertTrue(w.contains(luc(6, 2, 0)));    // 02:00 thu Ba, van thuoc khung thu Hai
            assertFalse(w.contains(luc(6, 23, 0)));  // 23:00 thu Ba, khung cua thu Ba khong bat
        }
    }

    @Nested
    @DisplayName("Ranh gioi ke tiep")
    class RanhGioi {

        @Test
        @DisplayName("Dang trong khung thi ranh gioi ke tiep la gio ket thuc")
        void dangTrongKhung() {
            ZonedDateTime boundary = khung("00:00", "07:00").nextBoundary(luc(5, 3, 0));
            assertEquals(luc(5, 7, 0), boundary);
        }

        @Test
        @DisplayName("Dang ngoai khung thi ranh gioi ke tiep la gio bat dau")
        void dangNgoaiKhung() {
            ZonedDateTime boundary = khung("00:00", "07:00").nextBoundary(luc(5, 12, 0));
            assertEquals(luc(6, 0, 0), boundary);
        }

        @Test
        @DisplayName("Khung vat qua nua dem, dang o 23:00 thi ranh gioi la 06:00 hom sau")
        void vatQuaNuaDem() {
            ZonedDateTime boundary = khung("22:00", "06:00").nextBoundary(luc(5, 23, 0));
            assertEquals(luc(6, 6, 0), boundary);
        }
    }
}
