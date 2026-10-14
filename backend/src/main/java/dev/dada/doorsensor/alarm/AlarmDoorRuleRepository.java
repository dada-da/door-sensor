package dev.dada.doorsensor.alarm;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AlarmDoorRuleRepository extends JpaRepository<AlarmDoorRule, String> {

    /** Khong co dong cau hinh thi cua kich hoat coi: cua moi tu dang ky van duoc bao ve. */
    default boolean sirenEnabled(String deviceId) {
        return findById(deviceId).map(AlarmDoorRule::isSirenEnabled).orElse(true);
    }
}
