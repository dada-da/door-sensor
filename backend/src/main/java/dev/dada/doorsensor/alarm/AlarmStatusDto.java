package dev.dada.doorsensor.alarm;

import java.time.Instant;

/**
 * Trang thai bao dong cho giao dien.
 *
 * @param level      DISABLED | DISARMED | ARMED | ARMED_SIREN_OFFLINE. Gia tri cuoi la
 *                   trang thai thu ba o muc 3.7: dang bao ve nhung coi khong keu duoc,
 *                   giao dien to mau canh bao.
 * @param sirenLink  UNKNOWN | ONLINE | OFFLINE
 */
public record AlarmStatusDto(
        boolean enabled,
        boolean armed,
        String level,
        String sirenId,
        String sirenLink,
        Instant sirenLastSeen,
        boolean sirenActive,
        Instant sirenActiveUntil,
        /** Ten cac cua dang tat bao dong. Hien lien tuc de khong quen bat lai. */
        java.util.List<String> disabledDoors
) {

    static String level(boolean enabled, boolean armed, boolean sirenOnline) {
        if (!enabled) return "DISABLED";
        if (!armed) return "DISARMED";
        return sirenOnline ? "ARMED" : "ARMED_SIREN_OFFLINE";
    }
}
