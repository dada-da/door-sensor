package dev.dada.doorsensor.alarm;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AlarmDoorRuleRepository extends JpaRepository<AlarmDoorRule, String> {

    /** Luat dang co hieu luc. Cua chua cau hinh thi dung mac dinh (chua luu). */
    default AlarmDoorRule effective(String deviceId) {
        return findById(deviceId).orElseGet(() -> AlarmDoorRule.defaults(deviceId));
    }
}
