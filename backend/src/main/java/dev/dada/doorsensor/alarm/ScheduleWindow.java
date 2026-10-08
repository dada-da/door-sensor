package dev.dada.doorsensor.alarm;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Khung gio bao ve. Logic thuan, khong phu thuoc Spring, khong phu thuoc CSDL,
 * nen kiem thu duoc truc tiep.
 *
 * <p>Day la noi duy nhat trong ca tinh nang co bay logic that: khung gio vat qua
 * nua dem. Khung 00:00 den 07:00 khong vat qua, nhung nguoi dung doi thanh
 * 22:00 den 06:00 la lap tuc roi vao truong hop gio bat dau lon hon gio ket thuc,
 * va phep so sanh thong thuong se sai ma khong bao loi gi.
 */
public record ScheduleWindow(
        boolean enabled,
        LocalTime start,
        LocalTime end,
        Set<DayOfWeek> days) {

    /** Thoi diem {@code now} co nam trong khung gio bao ve khong. */
    public boolean contains(ZonedDateTime now) {
        if (!enabled || days.isEmpty()) {
            return false;
        }
        // Khung rong: coi nhu tat. Quy uoc nay phai ghi ro trong tai lieu, vi
        // "ca ngay" cung la mot cach hieu hop ly khac.
        if (start.equals(end)) {
            return false;
        }

        LocalTime t = now.toLocalTime();
        boolean wraps = start.isAfter(end);

        boolean inTimeRange = wraps
                ? (!t.isBefore(start) || t.isBefore(end))   // vat qua nua dem
                : (!t.isBefore(start) && t.isBefore(end));  // trong cung mot ngay

        if (!inTimeRange) {
            return false;
        }
        return days.contains(owningDay(now, wraps));
    }

    /**
     * Khung gio thuoc ve ngay no bat dau, khong phai ngay dang hien hanh.
     *
     * <p>Vi du khung 22:00 den 06:00 chi bat vao thu Hai: luc 02:00 sang thu Ba,
     * ta van dang trong khung bat dau tu 22:00 thu Hai, nen phai kiem tra thu Hai.
     * Bo qua chi tiet nay thi bao ve tat som sau tieng moi dem ma khong ai biet.
     */
    private DayOfWeek owningDay(ZonedDateTime now, boolean wraps) {
        LocalDate date = now.toLocalDate();
        if (wraps && now.toLocalTime().isBefore(end)) {
            date = date.minusDays(1);
        }
        return date.getDayOfWeek();
    }

    /**
     * Thoi diem ke tiep ma trang thai bao ve theo lich thay doi.
     *
     * <p>Dung de dat han cho thao tac tay: bat hoac tat bao ve bang nut bam chi
     * co hieu luc toi ranh gioi ke tiep. Tat bao ve roi quen bat lai la loi
     * thuong gap nhat cua he thong bao dong that.
     */
    public ZonedDateTime nextBoundary(ZonedDateTime now) {
        if (!enabled || start.equals(end) || days.isEmpty()) {
            // Khong co ranh gioi nao: lay dau ngay hom sau cho co han.
            return now.toLocalDate().plusDays(1).atStartOfDay(now.getZone());
        }
        List<ZonedDateTime> candidates = new ArrayList<>(4);
        for (int d = 0; d <= 1; d++) {
            LocalDate date = now.toLocalDate().plusDays(d);
            candidates.add(ZonedDateTime.of(date, start, now.getZone()));
            candidates.add(ZonedDateTime.of(date, end, now.getZone()));
        }
        return candidates.stream()
                .filter(c -> c.isAfter(now))
                .min(Comparator.naturalOrder())
                .orElseThrow();
    }
}
