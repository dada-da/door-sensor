package dev.dada.doorsensor.alarm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dada.doorsensor.config.AlarmProperties;
import dev.dada.doorsensor.domain.Device;
import dev.dada.doorsensor.repo.DeviceRepository;
import dev.dada.doorsensor.service.DoorTransitionEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Kiem thu lop dieu phoi bao dong, khong can Spring, broker hay CSDL.
 *
 * <p>Dung fake tu viet thay vi Mockito de doc test thay ngay du lieu dang nam o
 * dau: {@code events} la bang alarm_event, {@code link.sent} la cac lenh da gui
 * xuong coi, {@code notifier.sent} la cac tin Telegram.
 *
 * <p>Lich mac dinh 00:00 den 07:00 moi ngay, coi keu 30 giay. 2026-10-05 la thu Hai.
 */
class AlarmServiceTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String SIREN = "siren-01";

    // ------------------------------------------------------------- fake

    static final class MutableClock extends Clock {
        Instant now;

        MutableClock(Instant now) { this.now = now; }

        void plusSeconds(long s) { now = now.plusSeconds(s); }

        void plusMillis(long ms) { now = now.plusMillis(ms); }

        @Override public ZoneId getZone() { return VN; }

        @Override public Clock withZone(ZoneId zone) { return this; }

        @Override public Instant instant() { return now; }
    }

    static final class FakeLink implements SirenLink {
        final List<String> sent = new ArrayList<>();
        final List<Boolean> armed = new ArrayList<>();
        boolean up = true;

        @Override public boolean sendCommand(String sirenId, String json) {
            if (up) sent.add(json);
            return up;
        }

        @Override public boolean publishArmed(boolean a) {
            if (up) armed.add(a);
            return up;
        }

        String last() { return sent.isEmpty() ? null : sent.get(sent.size() - 1); }
    }

    static final class RecordingNotifier extends AlarmNotifier {
        final List<String> sent = new ArrayList<>();

        RecordingNotifier() { super(null); }

        @Override public void intrusion(String door, Instant at, int s, boolean ok, boolean online) {
            sent.add("intrusion:" + door + ":" + s + ":" + (ok ? "ok" : "fail"));
        }

        @Override public void sirenOffline(Instant lastSeen) { sent.add("sirenOffline"); }

        @Override public void sirenBack() { sent.add("sirenBack"); }

        @Override public void openAtArmStart(List<String> doors) { sent.add("openAtArm:" + String.join(",", doors)); }

        @Override public void selfTestFailed(String reason) { sent.add("selfTestFailed"); }

        long count(String prefix) { return sent.stream().filter(s -> s.startsWith(prefix)).count(); }
    }

    @SuppressWarnings("unchecked")
    private static <T> T fakeRepo(Class<T> type, BiFunction<String, Object[], Object> impl) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, m, args) -> {
            if (m.getDeclaringClass() == Object.class) {
                return switch (m.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> type.getSimpleName() + "Fake";
                };
            }
            return impl.apply(m.getName(), args == null ? new Object[0] : args);
        });
    }

    // ------------------------------------------------------------- moi truong test

    MutableClock clock;
    AlarmSchedule schedule;
    final Map<Long, AlarmEvent> events = new LinkedHashMap<>();
    final List<SirenConnectivityEvent> conn = new ArrayList<>();
    final List<Device> devices = new ArrayList<>();
    final Map<String, AlarmDoorRule> doorRules = new LinkedHashMap<>();

    /** Luat cua mot cua, tao moi theo mac dinh neu chua co (giong effective + save). */
    AlarmDoorRule rule(String deviceId) {
        return doorRules.computeIfAbsent(deviceId, AlarmDoorRule::defaults);
    }
    final FakeLink link = new FakeLink();
    final RecordingNotifier notifier = new RecordingNotifier();
    long seq;
    AlarmService service;

    private AlarmService given(int hour, int minute, int second) {
        clock = new MutableClock(ZonedDateTime.of(
                LocalDate.of(2026, 10, 5), LocalTime.of(hour, minute, second), VN).toInstant());

        schedule = new AlarmSchedule();
        schedule.setEnabled(true);
        schedule.setStartTime(LocalTime.of(0, 0));
        schedule.setEndTime(LocalTime.of(7, 0));
        schedule.setSirenSeconds(30);

        AlarmScheduleRepository scheduleRepo = fakeRepo(AlarmScheduleRepository.class, (name, a) -> {
            if (name.equals("current")) return schedule;
            throw new UnsupportedOperationException(name);
        });

        AlarmEventRepository eventRepo = fakeRepo(AlarmEventRepository.class, (name, a) -> switch (name) {
            case "save" -> saveEvent((AlarmEvent) a[0]);
            case "findById" -> Optional.ofNullable(events.get((Long) a[0]));
            case "findByStoppedAtIsNull" -> events.values().stream()
                    .filter(e -> e.getStoppedAt() == null).toList();
            case "findFirstBySirenIdAndAckedAtIsNullAndCommandOkTrueAndTriggeredAtAfterOrderByTriggeredAtDesc" ->
                    events.values().stream()
                            .filter(e -> a[0].equals(e.getSirenId()) && e.getAckedAt() == null
                                    && e.isCommandOk() && e.getTriggeredAt().isAfter((Instant) a[1]))
                            .max(Comparator.comparing(AlarmEvent::getTriggeredAt));
            default -> throw new UnsupportedOperationException(name);
        });

        SirenConnectivityEventRepository connRepo = fakeRepo(SirenConnectivityEventRepository.class, (name, a) -> {
            if (name.equals("save")) {
                conn.add((SirenConnectivityEvent) a[0]);
                return a[0];
            }
            throw new UnsupportedOperationException(name);
        });

        DeviceRepository deviceRepo = fakeRepo(DeviceRepository.class, (name, a) -> {
            if (name.equals("findAllByOrderByLabelAsc")) return devices;
            throw new UnsupportedOperationException(name);
        });

        // Proxy chan ca phuong thuc default cua interface, nen phai tu cai sirenEnabled.
        AlarmDoorRuleRepository ruleRepo = fakeRepo(AlarmDoorRuleRepository.class, (name, a) -> switch (name) {
            case "effective" -> doorRules.getOrDefault((String) a[0], AlarmDoorRule.defaults((String) a[0]));
            default -> throw new UnsupportedOperationException(name);
        });
        // He thong "dang bao ve" khi co it nhat mot cua dang bao ve, nen phai co cua.
        if (devices.isEmpty()) {
            devices.add(new Device("door-01", "Cua tang 1"));
            devices.add(new Device("door-02", "Cua tang 2"));
        }

        AlarmProperties props = new AlarmProperties(true, SIREN, 1, 300, 60, 10, "-");
        service = new AlarmService(props, scheduleRepo, new AlarmDecisionService(clock), eventRepo,
                connRepo, deviceRepo, ruleRepo, link, notifier, new ObjectMapper(), clock);
        return service;
    }

    private AlarmEvent saveEvent(AlarmEvent e) {
        try {
            if (e.getId() == null) {
                Field f = AlarmEvent.class.getDeclaredField("id");
                f.setAccessible(true);
                f.set(e, ++seq);
            }
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        events.put(e.getId(), e);
        return e;
    }

    private DoorTransitionEvent opened(String deviceId) {
        return new DoorTransitionEvent(deviceId, "Cua " + deviceId,
                dev.dada.doorsensor.domain.DoorState.OPEN, clock.instant().minusSeconds(2), false);
    }

    private AlarmEvent onlyEvent() {
        assertEquals(1, events.size(), "so dong alarm_event");
        return events.values().iterator().next();
    }

    private void sirenOnline() {
        service.handleSirenStatus(new AlarmMessages.SirenStatus(SIREN, "online", false, clock.instant()));
    }

    // ------------------------------------------------------------- cua mo

    @Test
    @DisplayName("Cua mo trong gio bao ve: phat BEEP, ghi alarm_event, gui Telegram")
    void moKhiBaoVe() {
        given(3, 0, 0);
        DoorTransitionEvent e = opened("door-02");
        service.handleDoorTransition(e);

        assertEquals(List.of("{\"action\":\"BEEP\",\"duration_ms\":30000,\"reason\":\"DOOR_OPEN\"}"), link.sent);
        AlarmEvent row = onlyEvent();
        assertEquals("door-02", row.getDeviceId());
        assertEquals(SIREN, row.getSirenId());
        assertEquals(e.occurredAt(), row.getDoorOccurredAt());
        assertEquals(clock.instant(), row.getTriggeredAt());
        assertEquals(30_000, row.getDurationMs());
        assertTrue(row.isCommandOk());
        assertNull(row.getStoppedAt());
        assertEquals(List.of("intrusion:Cua door-02:30:ok"), notifier.sent);
        assertTrue(service.status().sirenActive());
    }

    @Test
    @DisplayName("Cua mo tiep khi coi dang keu: gia han cung mot dong, khong gui Telegram lan hai")
    void giaHan() {
        given(3, 0, 0);
        service.handleDoorTransition(opened("door-02"));
        clock.plusSeconds(10);
        service.handleDoorTransition(opened("door-01"));

        assertEquals(2, link.sent.size());
        assertTrue(link.last().contains("DOOR_OPEN_EXTEND"));
        assertEquals(40_000, onlyEvent().getDurationMs(), "10 giay da keu + 30 giay moi");
        assertEquals(1, notifier.count("intrusion"));
        assertEquals(clock.instant().plusSeconds(30), service.status().sirenActiveUntil());
    }

    @Test
    @DisplayName("Ngoai gio bao ve, tin dau tien, dong cua, su kien qua cu: khong keu")
    void khongKeu() {
        given(12, 0, 0);
        service.handleDoorTransition(opened("door-02"));

        given(3, 0, 0);
        service.handleDoorTransition(new DoorTransitionEvent("door-02", "x",
                dev.dada.doorsensor.domain.DoorState.OPEN, clock.instant(), true));
        service.handleDoorTransition(new DoorTransitionEvent("door-02", "x",
                dev.dada.doorsensor.domain.DoorState.CLOSED, clock.instant(), false));
        service.handleDoorTransition(new DoorTransitionEvent("door-02", "x",
                dev.dada.doorsensor.domain.DoorState.OPEN, clock.instant().minusSeconds(6 * 60), false));

        assertTrue(link.sent.isEmpty());
        assertTrue(events.isEmpty());
        assertTrue(notifier.sent.isEmpty());
    }

    @Test
    @DisplayName("Lenh khong toi duoc broker: ghi lai la that bai, bao Telegram, khong coi la dang keu")
    void lenhThatBai() {
        given(3, 0, 0);
        link.up = false;
        service.handleDoorTransition(opened("door-02"));

        AlarmEvent row = onlyEvent();
        assertFalse(row.isCommandOk());
        assertEquals(clock.instant(), row.getStoppedAt());
        assertNull(row.getStopSource());
        assertEquals(List.of("intrusion:Cua door-02:30:fail"), notifier.sent);
        assertFalse(service.status().sirenActive());
    }

    @Test
    @DisplayName("Cua bi tat coi rieng: khong keu, khong BAO DONG. Cua khac van keu")
    void tatCoiRiengMotCua() {
        given(3, 0, 0);
        rule("door-01").setSirenEnabled(false);

        service.handleDoorTransition(opened("door-01"));
        assertTrue(link.sent.isEmpty());
        assertTrue(events.isEmpty());
        assertTrue(notifier.sent.isEmpty());

        service.handleDoorTransition(opened("door-02"));
        assertEquals(1, link.sent.size());
        assertEquals("door-02", onlyEvent().getDeviceId());
    }

    @Test
    @DisplayName("Cua chua cau hinh thi mac dinh kich hoat coi")
    void macDinhKichHoat() {
        given(3, 0, 0);
        rule("door-01").setSirenEnabled(false);
        service.handleDoorTransition(opened("door-99"));
        assertEquals(1, link.sent.size());
    }

    // ------------------------------------------------------------- dung coi

    @Test
    @DisplayName("Het thoi luong: tick dong dot coi voi nguon TIMEOUT, moc dung bang han du kien")
    void hetGio() {
        given(3, 0, 0);
        service.handleDoorTransition(opened("door-02"));
        Instant expectedEnd = clock.instant().plusSeconds(30);

        clock.plusSeconds(29);
        service.tick();
        assertNull(onlyEvent().getStoppedAt(), "chua het gio");

        clock.plusSeconds(2);
        service.tick();
        assertEquals(expectedEnd, onlyEvent().getStoppedAt());
        assertEquals(AlarmEvent.StopSource.TIMEOUT, onlyEvent().getStopSource());
        assertFalse(service.status().sirenActive());
    }

    @Test
    @DisplayName("Tat tu xa: gui STOP, dong dot coi voi nguon REMOTE")
    void tatTuXa() {
        given(3, 0, 0);
        service.handleDoorTransition(opened("door-02"));
        clock.plusSeconds(5);

        assertEquals(AlarmService.CommandResult.OK, service.stop());
        assertEquals("{\"action\":\"STOP\"}", link.last());
        assertEquals(AlarmEvent.StopSource.REMOTE, onlyEvent().getStopSource());
        assertEquals(clock.instant(), onlyEvent().getStoppedAt());
        assertFalse(service.status().sirenActive());
    }

    @Test
    @DisplayName("Gui STOP that bai: giu nguyen trang thai, coi se tu tat theo han")
    void tatThatBai() {
        given(3, 0, 0);
        service.handleDoorTransition(opened("door-02"));
        link.up = false;

        assertEquals(AlarmService.CommandResult.SEND_FAILED, service.stop());
        assertNull(onlyEvent().getStoppedAt());
        assertTrue(service.status().sirenActive());
    }

    @Test
    @DisplayName("Tat bao ve trong luc coi keu: tat luon coi, nguon OVERRIDE")
    void tatBaoVeTatCoi() {
        given(3, 0, 0);
        service.handleDoorTransition(opened("door-02"));
        clock.plusSeconds(3);

        service.handleScheduleChanged(new AlarmMessages.ScheduleChanged("door-02", true));
        assertEquals(AlarmEvent.StopSource.OVERRIDE, onlyEvent().getStopSource());
    }

    @Test
    @DisplayName("Doi lich khi coi dang im: khong gui STOP thua")
    void doiLichKhiIm() {
        given(12, 0, 0);
        service.handleScheduleChanged(new AlarmMessages.ScheduleChanged("door-02", true));
        assertTrue(link.sent.isEmpty());
    }

    @Test
    @DisplayName("Tat bao ve cua khac trong luc coi keu vi door-02: khong dong vao coi")
    void tatBaoVeCuaKhac() {
        given(3, 0, 0);
        service.handleDoorTransition(opened("door-02"));
        service.handleScheduleChanged(new AlarmMessages.ScheduleChanged("door-01", true));
        assertNull(onlyEvent().getStoppedAt());
        assertTrue(service.status().sirenActive());
    }

    @Test
    @DisplayName("Tat coi tu Telegram: gui STOP, nguon TELEGRAM")
    void tatQuaTelegram() {
        given(3, 0, 0);
        service.handleDoorTransition(opened("door-02"));
        assertEquals(AlarmService.CommandResult.OK, service.stopFromTelegram());
        assertEquals("{\"action\":\"STOP\"}", link.last());
        assertEquals(AlarmEvent.StopSource.TELEGRAM, onlyEvent().getStopSource());
    }

    // ------------------------------------------------------------- lich rieng tung cua

    @Test
    @DisplayName("Khung gio rieng: 23h door-01 (22-06) keu, door-02 (00-07) khong")
    void khungGioRieng() {
        given(23, 0, 0);
        rule("door-01").setStartTime(LocalTime.of(22, 0));
        rule("door-01").setEndTime(LocalTime.of(6, 0));

        service.handleDoorTransition(opened("door-02"));
        assertTrue(link.sent.isEmpty(), "door-02 chua toi gio");

        service.handleDoorTransition(opened("door-01"));
        assertEquals(1, link.sent.size());
        assertEquals("door-01", onlyEvent().getDeviceId());
    }

    @Test
    @DisplayName("Tat bao ve tay mot cua: cua do im, cua khac van keu")
    void tatTayMotCua() {
        given(3, 0, 0);
        AlarmDoorRule r = rule("door-01");
        r.setOverrideMode(AlarmSchedule.Override.DISARMED);
        r.setOverrideUntil(clock.instant().plusSeconds(3600));

        service.handleDoorTransition(opened("door-01"));
        assertTrue(link.sent.isEmpty());
        service.handleDoorTransition(opened("door-02"));
        assertEquals(1, link.sent.size());
    }

    @Test
    @DisplayName("Bat bao ve tay giua trua cho mot cua: cua do keu, he thong bao ARMED")
    void batTayMotCua() {
        given(12, 0, 0);
        AlarmDoorRule r = rule("door-02");
        r.setOverrideMode(AlarmSchedule.Override.ARMED);
        r.setOverrideUntil(clock.instant().plusSeconds(3600));

        service.tick();
        assertEquals(List.of(true), link.armed);
        assertTrue(service.status().armed());
        service.handleDoorTransition(opened("door-01"));
        assertTrue(link.sent.isEmpty(), "door-01 van theo lich, chua toi gio");
        service.handleDoorTransition(opened("door-02"));
        assertEquals(1, link.sent.size());
    }

    @Test
    @DisplayName("Che do Luon bao ve: keu ca giua trua, khong het han")
    void luonBaoVe() {
        given(12, 0, 0);
        rule("door-02").applyMode(AlarmDoorRule.Mode.ALWAYS);
        assertEquals(AlarmDoorRule.Mode.ALWAYS, rule("door-02").mode());
        clock.plusSeconds(30 * 24 * 3600);   // mot thang sau van con
        service.handleDoorTransition(opened("door-02"));
        assertEquals(1, link.sent.size());
    }

    @Test
    @DisplayName("Che do Tat: lau dai, va co ten trong danh sach cua dang tat")
    void cheDoTat() {
        given(3, 0, 0);
        rule("door-01").applyMode(AlarmDoorRule.Mode.OFF);
        clock.plusSeconds(7 * 24 * 3600);
        service.handleDoorTransition(opened("door-01"));
        assertTrue(link.sent.isEmpty());
        assertEquals(List.of("Cua tang 1"), service.status().disabledDoors());

        rule("door-01").applyMode(AlarmDoorRule.Mode.SCHEDULE);
        service.handleDoorTransition(opened("door-01"));
        assertEquals(1, link.sent.size());
        assertTrue(service.status().disabledDoors().isEmpty());
    }

    @Test
    @DisplayName("Moi cua deu tat coi: he thong khong bao ve du dang trong khung gio")
    void tatHetCoi() {
        given(3, 0, 0);
        rule("door-01").setSirenEnabled(false);
        rule("door-02").setSirenEnabled(false);
        assertFalse(service.anyDoorArmed());
        assertEquals("DISARMED", service.status().level());
    }

    // ------------------------------------------------------------- thu coi

    @Test
    @DisplayName("Thu coi: gui TEST, tin state ON tu coi gan moc xac nhan")
    void thuCoi() {
        given(12, 0, 0);
        assertEquals(AlarmService.CommandResult.OK, service.test(false));
        assertEquals("{\"action\":\"TEST\"}", link.last());

        AlarmEvent row = onlyEvent();
        assertEquals("TEST", row.getReason());
        assertEquals(200, row.getDurationMs());

        clock.plusMillis(350);
        service.handleSirenState(new AlarmMessages.SirenState(SIREN, "{\"state\":\"ON\"}", false, clock.instant()));
        assertEquals(clock.instant(), row.getAckedAt());
        assertTrue(service.status().sirenLink().equals("ONLINE"), "tin song dua coi ve online");
    }

    @Test
    @DisplayName("Thu coi trong luc dang bao dong that: tu choi")
    void thuKhiDangKeu() {
        given(3, 0, 0);
        service.handleDoorTransition(opened("door-02"));
        assertEquals(AlarmService.CommandResult.BUSY, service.test(false));
        assertEquals(1, link.sent.size());
    }

    @Test
    @DisplayName("Tu thu khong duoc xac nhan: gui Telegram. Thu tay thi chi ghi nhat ky")
    void tuThuThatBai() {
        given(12, 0, 0);
        sirenOnline();
        service.scheduledSelfTest();
        clock.plusSeconds(11);
        service.tick();
        assertEquals(1, notifier.count("selfTestFailed"));

        service.test(false);
        clock.plusSeconds(11);
        service.tick();
        assertEquals(1, notifier.count("selfTestFailed"), "thu tay khong gui Telegram");
    }

    @Test
    @DisplayName("State giu lai khong tinh la xac nhan, cung khong tinh la tin song")
    void stateGiuLai() {
        given(12, 0, 0);
        service.test(false);
        service.handleSirenState(new AlarmMessages.SirenState(SIREN, "ON", true, clock.instant()));
        assertNull(onlyEvent().getAckedAt());
        assertEquals("UNKNOWN", service.status().sirenLink());
    }

    // ------------------------------------------------------------- che do bao ve

    @Test
    @DisplayName("Toi gio bat bao ve: ghi armed giu lai, nhac neu cua dang mo")
    void batDauBaoVe() {
        Device door = new Device("door-01", "Cua tang 1");
        door.setLastState(dev.dada.doorsensor.domain.DoorState.OPEN);
        devices.add(door);
        devices.add(new Device("door-02", "Cua tang 2"));
        Device door3 = new Device("door-03", "Cua phu");
        door3.setLastState(dev.dada.doorsensor.domain.DoorState.OPEN);
        devices.add(door3);
        rule("door-03").setSirenEnabled(false);   // tat coi: mo san cung khong can nhac

        given(23, 59, 50);
        service.tick();
        assertEquals(List.of(false), link.armed, "lan dau ghi trang thai hien tai");
        assertTrue(notifier.sent.isEmpty(), "lan tick dau khong tinh la vua bat");

        clock.plusSeconds(15);
        service.tick();
        assertEquals(List.of(false, true), link.armed);
        assertEquals(List.of("openAtArm:Cua tang 1"), notifier.sent);

        service.tick();
        assertEquals(2, link.armed.size(), "khong ghi lai khi khong doi");
    }

    @Test
    @DisplayName("Ghi armed that bai thi thu lai o tick sau; broker noi lai thi ghi lai")
    void ghiArmedThuLai() {
        given(3, 0, 0);
        link.up = false;
        service.tick();
        assertTrue(link.armed.isEmpty());

        link.up = true;
        service.tick();
        assertEquals(List.of(true), link.armed);

        service.onBrokerConnected(new AlarmMessages.BrokerConnected(true));
        assertEquals(List.of(true, true), link.armed);
    }

    // ------------------------------------------------------------- giam sat coi

    @Test
    @DisplayName("Coi mat lien lac khi dang bao ve: canh bao mot lan, ket noi lai thi bao")
    void coiMatLienLac() {
        given(3, 0, 0);
        sirenOnline();
        clock.plusSeconds(5);
        service.handleSirenStatus(new AlarmMessages.SirenStatus(SIREN, "offline", false, clock.instant()));
        assertEquals("ARMED_SIREN_OFFLINE", service.status().level());

        clock.plusSeconds(59);
        service.tick();
        assertEquals(0, notifier.count("sirenOffline"));
        clock.plusSeconds(2);
        service.tick();
        service.tick();
        assertEquals(1, notifier.count("sirenOffline"));

        sirenOnline();
        assertEquals(1, notifier.count("sirenBack"));
        assertEquals("ARMED", service.status().level());
        assertEquals(3, conn.size(), "online, offline, online");
    }

    @Test
    @DisplayName("Tin tu node coi khac bi bo qua")
    void coiLa() {
        given(3, 0, 0);
        service.handleSirenStatus(new AlarmMessages.SirenStatus("siren-99", "online", false, clock.instant()));
        assertEquals("UNKNOWN", service.status().sirenLink());
        assertTrue(conn.isEmpty());
    }

    // ------------------------------------------------------------- khoi dong lai

    @Test
    @DisplayName("Khoi dong lai: dot coi con han duoc khoi phuc, dot da het gio duoc dong")
    void khoiPhuc() {
        given(3, 0, 0);
        AlarmEvent old = saveEvent(new AlarmEvent("door-01", SIREN, null,
                clock.instant().minusSeconds(600), 30_000, "DOOR_OPEN", true));
        AlarmEvent live = saveEvent(new AlarmEvent("door-02", SIREN, null,
                clock.instant().minusSeconds(10), 30_000, "DOOR_OPEN", true));

        service.restore();

        assertEquals(AlarmEvent.StopSource.TIMEOUT, old.getStopSource());
        assertEquals(old.expectedEnd(), old.getStoppedAt());
        assertNull(live.getStoppedAt());
        assertTrue(service.status().sirenActive());
        assertEquals(live.expectedEnd(), service.status().sirenActiveUntil());
        assertNotNull(service.status().sirenActiveUntil());
    }

    @Test
    @DisplayName("Doc state: JSON hai kieu ten truong va chuoi tran")
    void docState() {
        given(3, 0, 0);
        assertEquals("ON", service.parseSirenState("{\"state\":\"on\"}"));
        assertEquals("OFF", service.parseSirenState("{\"siren\":\"OFF\",\"remaining_ms\":0}"));
        assertEquals("ON", service.parseSirenState(" ON "));
        assertNull(service.parseSirenState("{hong"));
    }
}
