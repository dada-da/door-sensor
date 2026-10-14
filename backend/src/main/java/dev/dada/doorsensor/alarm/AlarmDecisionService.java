package dev.dada.doorsensor.alarm;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.ZonedDateTime;

/**
 * Quyet dinh co keu coi hay khong.
 *
 * <p>Co y khong phu thuoc MQTT: nhan vao mot su kien, tra ra mot quyet dinh.
 * Viec gui lenh di dau la chuyen cua tang khac. Nho vay kiem thu duoc toan bo
 * luat quyet dinh, ke ca bon truong hop bien trong bang 3.4, ma khong can bo
 * trung gian nao dang chay.
 */
@Service
public class AlarmDecisionService {

    /** Tran cung phia may chu. Firmware con mot tran cung nua, doc lap. */
    public static final int MAX_SIREN_SECONDS = 120;

    private final Clock clock;

    public AlarmDecisionService(Clock clock) {
        this.clock = clock;
    }

    /**
     * He thong co dang o che do bao ve khong.
     *
     * <p>Thao tac tay de len lich, nhung het hieu luc o ranh gioi khung gio ke
     * tiep. Qua han thi quay ve theo lich.
     */
    public boolean isArmed(ArmingRule rule) {
        ZonedDateTime now = ZonedDateTime.now(clock);

        if (rule.getOverrideMode() != AlarmSchedule.Override.NONE
                && rule.getOverrideUntil() != null
                && now.toInstant().isBefore(rule.getOverrideUntil())) {
            return rule.getOverrideMode() == AlarmSchedule.Override.ARMED;
        }
        return rule.toWindow().contains(now);
    }

    /** Thoi diem {@code at} co nam trong khung gio cua luat khong, bo qua thao tac tay. */
    public boolean inWindow(ArmingRule rule, java.time.Instant at) {
        return rule.toWindow().contains(at.atZone(clock.getZone()));
    }

    /**
     * Quyet dinh cho mot su kien cua.
     *
     * @param event        su kien da qua chong trung lap o tang tren
     * @param sirenActive  coi co dang keu khong, de phan biet kich hoat moi voi gia han
     */
    public AlarmDecision decide(DoorEvent event, AlarmSchedule schedule, boolean sirenActive) {
        return decide(event, schedule, schedule.getSirenSeconds(), sirenActive);
    }

    /**
     * Quyet dinh voi luat bao ve cua rieng mot cua. Thoi luong coi la cau hinh
     * chung vi chi co mot coi.
     */
    public AlarmDecision decide(DoorEvent event, ArmingRule rule, int sirenSeconds, boolean sirenActive) {
        // Chi phan ung voi chuyen sang mo. Dong cua khong bao gio kich hoat coi.
        if (event.state() != DoorState.OPEN) {
            return AlarmDecision.none("NOT_AN_OPENING");
        }

        // Cua dang mo san luc khung gio bat dau thi khong keu, vi moi dem quen
        // dong cua la coi hu trong khi chang co ai vao. Tang tren danh dau
        // truong hop nay bang transition = false.
        if (!event.transition()) {
            return AlarmDecision.none("ALREADY_OPEN_AT_ARM_TIME");
        }

        if (!isArmed(rule)) {
            return AlarmDecision.none("NOT_ARMED");
        }

        int seconds = Math.min(sirenSeconds, MAX_SIREN_SECONDS);
        long durationMs = seconds * 1000L;

        // Cua mo tiep trong luc coi dang keu: gia han, khong chong lenh.
        String reason = sirenActive ? "DOOR_OPEN_EXTEND" : "DOOR_OPEN";
        return AlarmDecision.beep(durationMs, reason);
    }

    /** Han cho mot thao tac tay: ranh gioi khung gio ke tiep. */
    public ZonedDateTime overrideExpiry(ArmingRule rule) {
        return rule.toWindow().nextBoundary(ZonedDateTime.now(clock));
    }
}
