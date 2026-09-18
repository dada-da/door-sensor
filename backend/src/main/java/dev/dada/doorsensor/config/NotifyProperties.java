package dev.dada.doorsensor.config;

import java.time.LocalTime;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notify")
public record NotifyProperties(
        boolean enabled,
        Telegram telegram,
        DoorOpen doorOpen,
        Offline offline,
        Battery battery,
        QuietHours quietHours
) {

    public record Telegram(String botToken, String chatId) {
        public boolean configured() {
            return botToken != null && !botToken.isBlank()
                    && chatId != null && !chatId.isBlank();
        }
    }

    public record DoorOpen(
            boolean enabled,
            long minIntervalSeconds,
            boolean onlyDuringQuietHours
    ) {}

    public record Offline(
            boolean enabled,
            long graceSeconds
    ) {}

    public record Battery(
            boolean enabled,
            int thresholdMv,
            int rearmAboveMv,
            long cooldownHours
    ) {}

    public record QuietHours(String start, String end, String zone) {
        public LocalTime startTime() { return LocalTime.parse(start); }
        public LocalTime endTime()   { return LocalTime.parse(end); }
        public ZoneId zoneId()       { return ZoneId.of(zone); }
    }
}
