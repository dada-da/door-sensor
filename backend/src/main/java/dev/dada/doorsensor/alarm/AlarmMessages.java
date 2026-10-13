package dev.dada.doorsensor.alarm;

import java.time.Instant;

/**
 * Cac su kien noi bo giua tang MQTT bao dong va AlarmService.
 *
 * <p>Dung su kien Spring thay vi goi thang de tranh phu thuoc vong: gateway can
 * bao cho service khi co tin den, service can gateway de phat lenh. Them nua,
 * service xu ly cac su kien nay tren luong rieng, nen khong bao gio publish
 * QoS 1 tu trong callback cua Paho (lam vay co the treo cho PUBACK mai mai,
 * vi PUBACK can chinh luong callback do de xu ly).
 */
public final class AlarmMessages {

    private AlarmMessages() {
    }

    /** Tin tren home/alarm/{id}/status. */
    public record SirenStatus(String sirenId, String payload, boolean retained, Instant receivedAt) {
    }

    /** Tin tren home/alarm/{id}/state. */
    public record SirenState(String sirenId, String payload, boolean retained, Instant receivedAt) {
    }

    /** Client bao dong vua ket noi (lan dau hoac sau khi mat ket noi). */
    public record BrokerConnected(boolean reconnect) {
    }

    /**
     * Lich hoac thao tac tay vua doi. Phat sau khi giao dich ghi CSDL da commit.
     *
     * @param disarmedByUser true neu nguoi dung vua bam tat bao ve
     */
    public record ScheduleChanged(boolean disarmedByUser) {
    }
}
