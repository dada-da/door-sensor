package dev.dada.doorsensor.alarm;

import java.time.Instant;

/**
 * Thu quyet dinh mot doi tuong co dang duoc bao ve khong: khung gio cong voi
 * thao tac tay co han.
 *
 * <p>Ban dau chi co mot lich chung (AlarmSchedule). Tu 14/10 moi cua co lich
 * rieng (AlarmDoorRule). Ca hai cung dang nay nen AlarmDecisionService, phan da
 * co kiem thu cho bang 3.4, dung chung duoc ma khong phai viet lai.
 */
public interface ArmingRule {

    ScheduleWindow toWindow();

    AlarmSchedule.Override getOverrideMode();

    /** Han cua thao tac tay, null neu khong co. */
    Instant getOverrideUntil();
}
