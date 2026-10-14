package dev.dada.doorsensor.alarm;

import dev.dada.doorsensor.notify.OffHoursLookup;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * "Ngoai gio thuong le" cua tin Telegram mo cua = nam trong khung gio bao ve cua
 * chinh cua do. Chi xet khung gio, khong xet thao tac tay va cong tac coi: bam
 * "bat bao ve ngay" luc 14h khong bien 14h thanh gio bat thuong.
 */
@Component
public class DoorOffHours implements OffHoursLookup {

    private final AlarmDoorRuleRepository rules;
    private final AlarmDecisionService decisions;

    public DoorOffHours(AlarmDoorRuleRepository rules, AlarmDecisionService decisions) {
        this.rules = rules;
        this.decisions = decisions;
    }

    @Override
    public boolean isOffHours(String deviceId, Instant at) {
        return decisions.inWindow(rules.effective(deviceId), at);
    }
}
