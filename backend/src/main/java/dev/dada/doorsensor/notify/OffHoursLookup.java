package dev.dada.doorsensor.notify;

import java.time.Instant;

/**
 * Thoi diem {@code at} co nam ngoai gio thuong le cua cua {@code deviceId} khong.
 *
 * <p>Tu 14/10 khung gio nay chinh la khung gio bao ve rieng cua tung cua (bang
 * alarm_door_rule), thay cho app.notify.quiet-hours dung chung. Tach thanh giao
 * dien de goi notify khong phu thuoc goi alarm.
 */
public interface OffHoursLookup {

    boolean isOffHours(String deviceId, Instant at);
}
