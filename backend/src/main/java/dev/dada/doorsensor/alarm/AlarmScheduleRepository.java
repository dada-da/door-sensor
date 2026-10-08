package dev.dada.doorsensor.alarm;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AlarmScheduleRepository extends JpaRepository<AlarmSchedule, Short> {

    /** Luon chi co mot dong, id = 1. */
    default AlarmSchedule current() {
        return findById((short) 1).orElseThrow(
                () -> new IllegalStateException("Thieu dong cau hinh alarm_schedule id=1"));
    }
}
