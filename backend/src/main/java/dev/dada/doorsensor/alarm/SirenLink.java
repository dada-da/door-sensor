package dev.dada.doorsensor.alarm;

/**
 * Duong lenh xuong node coi. Tach thanh giao dien de kiem thu AlarmService ma
 * khong can broker.
 */
public interface SirenLink {

    /**
     * Gui lenh toi home/alarm/{sirenId}/cmd, QoS 1, khong giu lai.
     *
     * @return true neu broker da xac nhan (PUBACK). False khi mat ket noi hoac
     *         het thoi gian cho. True khong co nghia la coi da keu: lenh con phai
     *         tu broker toi node, xac nhan that la tin state ON tu node.
     */
    boolean sendCommand(String sirenId, String json);

    /** Ghi home/alarm/armed, QoS 1, giu lai. */
    boolean publishArmed(boolean armed);
}
