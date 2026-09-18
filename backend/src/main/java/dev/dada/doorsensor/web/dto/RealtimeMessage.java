package dev.dada.doorsensor.web.dto;

public record RealtimeMessage(
        String type,
        DeviceStatusDto device,
        EventDto event
) {
    public static RealtimeMessage door(DeviceStatusDto device, EventDto event) {
        return new RealtimeMessage("door", device, event);
    }

    public static RealtimeMessage device(DeviceStatusDto device) {
        return new RealtimeMessage("device", device, null);
    }
}
