package dev.dada.doorsensor.domain;

public enum DoorState {
    OPEN,
    CLOSED;

    public static DoorState parse(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toUpperCase()) {
            case "OPEN", "1", "TRUE", "HIGH" -> OPEN;
            case "CLOSED", "CLOSE", "0", "FALSE", "LOW" -> CLOSED;
            default -> null;
        };
    }
}
