package dev.dada.doorsensor.alarm;

/**
 * Ket qua quyet dinh. Truong {@code reason} khong anh huong hanh vi, chi de ghi
 * nhat ky va de doc lai khi phan tich so lieu cho muc 5.6.
 */
public record AlarmDecision(Action action, long durationMs, String reason) {

    public enum Action { NONE, BEEP }

    public static AlarmDecision none(String reason) {
        return new AlarmDecision(Action.NONE, 0, reason);
    }

    public static AlarmDecision beep(long durationMs, String reason) {
        return new AlarmDecision(Action.BEEP, durationMs, reason);
    }

    public boolean shouldBeep() {
        return action == Action.BEEP;
    }
}
