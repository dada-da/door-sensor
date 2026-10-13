package dev.dada.doorsensor.service;

import dev.dada.doorsensor.domain.DoorState;
import java.time.Instant;

/**
 * Cua vua doi trang thai that, da ghi xong vao CSDL. DoorService phat sau khi
 * giao dich commit, cac phan bo sung (bao dong) nghe ma khong can DoorService
 * biet gi ve chung.
 *
 * @param firstReport true neu day la tin dau tien cua thiet bi, chua co trang thai
 *                    truoc do de so sanh. Khong biet cua vua mo hay da mo tu lau,
 *                    nen phia bao dong coi nhu khong phai mot lan mo moi.
 */
public record DoorTransitionEvent(
        String deviceId,
        String label,
        DoorState state,
        Instant occurredAt,
        boolean firstReport
) {
}
