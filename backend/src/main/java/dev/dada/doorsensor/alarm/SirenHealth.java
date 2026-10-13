package dev.dada.doorsensor.alarm;

import java.time.Duration;
import java.time.Instant;

/**
 * Theo doi node coi con song hay khong, va quyet dinh khi nao canh bao (CN-16).
 *
 * <p>Logic thuan, khong phu thuoc Spring, khong phu thuoc MQTT, khong tu goi
 * dong ho: moi thoi diem duoc truyen vao. Lop goi chiu trach nhiem dong bo hoa.
 *
 * <p>Ba diem de sai ma lop nay xu ly:
 * <ul>
 *   <li><b>Chua biet khac voi da mat.</b> Vua khoi dong may chu thi chua biet
 *       coi song hay chet. Coi nhu mat lien lac tu luc khoi dong, de neu coi
 *       chua tung ket noi ma he thong bat bao ve thi van canh bao. Nhung khong
 *       ghi su kien mat ket noi, vi khong co gi thuc su vua xay ra.</li>
 *   <li><b>Thong diep giu lai phat lai.</b> Moi lan may chu dang ky lai, broker
 *       gui lai status giu lai. Neu ghi moi lan nhu vay thanh mot su kien thi
 *       moi lan khoi dong lai may chu la mot dong ao trong bang 5.2. Chi ghi khi
 *       trang thai doi tu trang thai da biet, hoac khi tin la tin song.</li>
 *   <li><b>Chi canh bao khi dang bao ve.</b> Ngoai khung gio thi khong lam
 *       phien. Nhung coi chet tu chieu, toi bat bao ve, thi phai canh bao ngay
 *       luc bat, khong phai im vi luc chet chua bao ve.</li>
 * </ul>
 */
public final class SirenHealth {

    public enum Link { UNKNOWN, ONLINE, OFFLINE }

    public enum Source { REPORTED, LWT, WATCHDOG }

    public enum Alert { NONE, OFFLINE_WHILE_ARMED, RECONNECTED }

    /** Mot lan doi trang thai can ghi vao siren_connectivity_event. */
    public record Change(boolean online, Source source) {
    }

    /** Ket qua moi buoc: co the co mot thay doi can ghi va mot canh bao can gui. */
    public record Result(Change change, Alert alert) {
        static final Result NOTHING = new Result(null, Alert.NONE);

        public boolean hasChange() {
            return change != null;
        }
    }

    private final Duration offlineAfter;
    private final Duration grace;

    private Link link = Link.UNKNOWN;
    private Instant offlineSince;
    private Instant lastSeen;
    private boolean alerted;

    /**
     * @param offlineAfter qua bao lau khong nhan tin song nao thi coi la chet,
     *                     du broker chua phat di chuc (node treo, TCP con song)
     * @param grace        cho bao lau sau khi mat lien lac moi canh bao, de mot
     *                     lan nhay diem phat Wi-Fi khong thanh mot tin Telegram
     * @param startedAt    thoi diem may chu khoi dong
     */
    public SirenHealth(Duration offlineAfter, Duration grace, Instant startedAt) {
        this.offlineAfter = offlineAfter;
        this.grace = grace;
        this.offlineSince = startedAt;
    }

    /**
     * Tin tren home/alarm/{id}/status.
     *
     * @param live false neu la thong diep giu lai broker phat lai luc dang ky
     */
    public Result onStatus(boolean online, boolean live, Instant now) {
        return online ? goOnline(live, now) : goOffline(Source.LWT, live, now);
    }

    /**
     * Mot tin song bat ky tu node (state, nhip bao). Lop goi chi duoc goi ham nay
     * voi tin khong phai giu lai: state giu lai van con nguyen sau khi node chet.
     */
    public Result onSeen(Instant now) {
        lastSeen = now;
        if (link == Link.ONLINE) {
            return Result.NOTHING;
        }
        return goOnline(true, now);
    }

    /**
     * Goi dinh ky.
     *
     * @param armed he thong co dang o che do bao ve khong
     */
    public Result tick(boolean armed, Instant now) {
        Change change = null;

        // Di chuc co diem mu: node treo o tang ung dung, TCP van song, broker
        // khong phat gi. Can nhip bao rieng de bat truong hop nay.
        if (link == Link.ONLINE && lastSeen != null && now.isAfter(lastSeen.plus(offlineAfter))) {
            link = Link.OFFLINE;
            offlineSince = now;
            change = new Change(false, Source.WATCHDOG);
        }

        Alert alert = Alert.NONE;
        if (link != Link.ONLINE && armed && !alerted
                && offlineSince != null && !now.isBefore(offlineSince.plus(grace))) {
            alerted = true;
            alert = Alert.OFFLINE_WHILE_ARMED;
        }
        return new Result(change, alert);
    }

    private Result goOnline(boolean live, Instant now) {
        lastSeen = now;
        if (link == Link.ONLINE) {
            return Result.NOTHING;
        }
        Link prev = link;
        link = Link.ONLINE;
        offlineSince = null;

        Change change = (prev == Link.OFFLINE || live) ? new Change(true, Source.REPORTED) : null;
        Alert alert = alerted ? Alert.RECONNECTED : Alert.NONE;
        alerted = false;
        return new Result(change, alert);
    }

    private Result goOffline(Source source, boolean live, Instant now) {
        if (link == Link.OFFLINE) {
            return Result.NOTHING;
        }
        Link prev = link;
        link = Link.OFFLINE;
        // Tu UNKNOWN sang OFFLINE: giu moc khoi dong, vi coi da chet it nhat tu do.
        if (prev == Link.ONLINE || offlineSince == null) {
            offlineSince = now;
        }
        Change change = (prev == Link.ONLINE || live) ? new Change(false, source) : null;
        return new Result(change, Alert.NONE);
    }

    public Link link() { return link; }

    public boolean isOnline() { return link == Link.ONLINE; }

    public Instant lastSeen() { return lastSeen; }

    public Instant offlineSince() { return offlineSince; }
}
