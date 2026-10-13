package dev.dada.doorsensor.alarm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dada.doorsensor.alarm.SirenHealth.Alert;
import dev.dada.doorsensor.alarm.SirenHealth.Result;
import dev.dada.doorsensor.alarm.SirenHealth.Source;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Giam sat node coi (CN-16). Moi thoi diem truyen tay, khong phu thuoc gio chay.
 */
class SirenHealthTest {

    private static final Instant T0 = Instant.parse("2026-10-12T15:00:00Z");
    private static final Duration OFFLINE_AFTER = Duration.ofSeconds(300);
    private static final Duration GRACE = Duration.ofSeconds(60);

    private SirenHealth fresh() {
        return new SirenHealth(OFFLINE_AFTER, GRACE, T0);
    }

    private static Instant at(long seconds) {
        return T0.plusSeconds(seconds);
    }

    @Test
    @DisplayName("Coi online roi di chuc offline khi dang bao ve: canh bao sau dung khoang cho")
    void offlineKhiDangBaoVe() {
        SirenHealth h = fresh();
        h.onStatus(true, true, at(0));

        Result r = h.onStatus(false, true, at(100));
        assertEquals(new SirenHealth.Change(false, Source.LWT), r.change());
        assertEquals(Alert.NONE, r.alert());

        assertEquals(Alert.NONE, h.tick(true, at(159)).alert(), "chua het khoang cho");
        assertEquals(Alert.OFFLINE_WHILE_ARMED, h.tick(true, at(160)).alert());
        assertEquals(Alert.NONE, h.tick(true, at(200)).alert(), "chi canh bao mot lan moi dot");
    }

    @Test
    @DisplayName("Mat lien lac ngoai khung gio thi im, bat bao ve luc sau thi canh bao ngay")
    void chetTuChieuToiBatBaoVe() {
        SirenHealth h = fresh();
        h.onStatus(true, true, at(0));
        h.onStatus(false, true, at(10));

        assertEquals(Alert.NONE, h.tick(false, at(3600)).alert(), "ngoai khung gio khong lam phien");
        assertEquals(Alert.OFFLINE_WHILE_ARMED, h.tick(true, at(3610)).alert(),
                "bat bao ve khi coi da chet tu truoc: canh bao ngay, khong cho them");
    }

    @Test
    @DisplayName("Ket noi lai sau khi da canh bao thi bao da ket noi lai, mot lan")
    void ketNoiLai() {
        SirenHealth h = fresh();
        h.onStatus(true, true, at(0));
        h.onStatus(false, true, at(10));
        h.tick(true, at(100));

        Result r = h.onStatus(true, true, at(200));
        assertEquals(Alert.RECONNECTED, r.alert());
        assertEquals(new SirenHealth.Change(true, Source.REPORTED), r.change());
        assertEquals(Alert.NONE, h.onSeen(at(210)).alert());
    }

    @Test
    @DisplayName("Mat roi co lai trong khoang cho thi khong gui gi")
    void nhayDiemPhat() {
        SirenHealth h = fresh();
        h.onStatus(true, true, at(0));
        h.onStatus(false, true, at(10));
        assertEquals(Alert.NONE, h.tick(true, at(40)).alert());

        Result back = h.onStatus(true, true, at(50));
        assertEquals(Alert.NONE, back.alert(), "chua canh bao thi khong bao ket noi lai");
        assertTrue(back.hasChange(), "nhung van ghi su kien de dem so lan mat ket noi");
        assertEquals(Alert.NONE, h.tick(true, at(500)).alert());
    }

    @Test
    @DisplayName("Thong diep giu lai luc khoi dong khong sinh su kien ao")
    void giuLaiKhongSinhSuKien() {
        SirenHealth h = fresh();
        Result r = h.onStatus(true, false, at(1));
        assertFalse(r.hasChange());
        assertTrue(h.isOnline());

        SirenHealth h2 = fresh();
        Result r2 = h2.onStatus(false, false, at(1));
        assertFalse(r2.hasChange());
        assertEquals(T0, h2.offlineSince(), "giu moc khoi dong, coi chet it nhat tu do");
    }

    @Test
    @DisplayName("Chua tung nghe thay coi ma bat bao ve: canh bao sau khoang cho tinh tu luc khoi dong")
    void chuaTungKetNoi() {
        SirenHealth h = fresh();
        assertEquals(SirenHealth.Link.UNKNOWN, h.link());
        assertEquals(Alert.NONE, h.tick(true, at(30)).alert());
        Result r = h.tick(true, at(60));
        assertEquals(Alert.OFFLINE_WHILE_ARMED, r.alert());
        assertFalse(r.hasChange(), "khong co gi vua xay ra, khong ghi su kien");
        assertNull(h.lastSeen());
    }

    @Test
    @DisplayName("Node treo, TCP con song: watchdog bat duoc khi qua nguong khong co tin song")
    void watchdog() {
        SirenHealth h = fresh();
        h.onStatus(true, true, at(0));
        h.onSeen(at(60));

        assertFalse(h.tick(true, at(360)).hasChange(), "dung bang nguong chua tinh la chet");
        Result r = h.tick(true, at(361));
        assertEquals(new SirenHealth.Change(false, Source.WATCHDOG), r.change());
        assertEquals(Alert.NONE, r.alert(), "van cho khoang cho truoc khi canh bao");
        assertEquals(Alert.OFFLINE_WHILE_ARMED, h.tick(true, at(421)).alert());
    }

    @Test
    @DisplayName("Tin song bat ky dua coi tu offline ve online")
    void tinSongDuaVeOnline() {
        SirenHealth h = fresh();
        h.onStatus(true, true, at(0));
        h.onStatus(false, true, at(10));
        Result r = h.onSeen(at(20));
        assertTrue(h.isOnline());
        assertEquals(new SirenHealth.Change(true, Source.REPORTED), r.change());
    }

    @Test
    @DisplayName("Di chuc lap lai khong ghi trung")
    void diChucLapLai() {
        SirenHealth h = fresh();
        h.onStatus(true, true, at(0));
        assertTrue(h.onStatus(false, true, at(10)).hasChange());
        assertFalse(h.onStatus(false, false, at(11)).hasChange());
        assertFalse(h.onStatus(false, true, at(12)).hasChange());
    }
}
