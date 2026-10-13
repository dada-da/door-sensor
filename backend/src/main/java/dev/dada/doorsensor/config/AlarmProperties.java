package dev.dada.doorsensor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cau hinh phan bao dong chay that (phat lenh, giam sat node coi).
 * Lich bao ve van nam trong bang alarm_schedule, khong nam o day.
 *
 * @param enabled                  tat thi khong ket noi MQTT, khong phat lenh, khong canh bao
 * @param sirenId                  ma node coi, phan {id} cua home/alarm/{id}/...
 * @param qos                      QoS cho lenh va thong diep armed. Lenh bat buoc QoS 1
 * @param sirenOfflineAfterSeconds khong nhan tin song nao trong khoang nay thi coi la chet,
 *                                 phai lon hon nhip bao cua firmware coi it nhat gap doi
 * @param sirenOfflineGraceSeconds cho them truoc khi gui Telegram, chong bao nham khi nhay diem phat
 * @param ackTimeoutSeconds        lenh thu khong duoc xac nhan trong khoang nay thi coi la that bai
 * @param selfTestCron             lich tu thu coi, "-" la tat
 */
@ConfigurationProperties(prefix = "app.alarm")
public record AlarmProperties(
        boolean enabled,
        String sirenId,
        int qos,
        long sirenOfflineAfterSeconds,
        long sirenOfflineGraceSeconds,
        long ackTimeoutSeconds,
        String selfTestCron
) {
}
