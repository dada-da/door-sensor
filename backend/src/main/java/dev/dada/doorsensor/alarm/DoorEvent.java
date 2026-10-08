package dev.dada.doorsensor.alarm;

import java.time.Instant;

/**
 * Su kien cua, dang thuan de truyen vao tang quyet dinh.
 *
 * @param transition true neu day la mot lan doi trang thai that. False cho cac
 *                   truong hop nhu quet trang thai luc khung gio bat dau, hoac
 *                   nhip bao con song dong bo lai trang thai.
 */
public record DoorEvent(
        String deviceId,
        DoorState state,
        Instant occurredAt,
        boolean transition) {
}
