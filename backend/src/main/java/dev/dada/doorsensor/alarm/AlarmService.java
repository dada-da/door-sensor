package dev.dada.doorsensor.alarm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dada.doorsensor.config.AlarmProperties;
import dev.dada.doorsensor.domain.Device;
import dev.dada.doorsensor.repo.DeviceRepository;
import dev.dada.doorsensor.service.DoorTransitionEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Dieu phoi bao dong: nghe su kien cua, hoi AlarmDecisionService, phat lenh
 * xuong node coi, ghi alarm_event, giam sat node coi, gui Telegram.
 *
 * <p>Luat quyet dinh khong nam o day ma o AlarmDecisionService (da co kiem thu
 * bang 3.4). Lop nay chi noi cac mieng lai voi nhau va giu trang thai chay.
 *
 * <p><b>Dong bo hoa.</b> Moi ham doi trang thai deu {@code synchronized}. Su kien
 * tu cua va tu MQTT den tren luong alarmExecutor (mot luong, giu thu tu), tick
 * den tren luong lap lich, nut bam den tren luong web. Khoa chung dam bao "bat"
 * va "gia han" khong chen lan nhau.
 *
 * <p><b>Trang thai trong bo nho.</b> Coi dang keu toi bao gio, che do bao ve lan
 * truoc. Mat khi khoi dong lai, nhung khoi phuc duoc: dot coi dang mo lay lai tu
 * alarm_event, trang thai coi lay lai tu thong diep giu lai tren broker.
 */
@Service
public class AlarmService {

    private static final Logger log = LoggerFactory.getLogger(AlarmService.class);

    /** Su kien cu hon muc nay thi khong keu nua: coi keu muon vai phut chi gay hoang mang. */
    static final Duration MAX_EVENT_AGE = Duration.ofMinutes(5);
    /** Tin state ON trong khoang nay sau lenh thi tinh la xac nhan cua lenh do. */
    static final Duration ACK_WINDOW = Duration.ofSeconds(30);
    static final int TEST_DURATION_MS = 200;

    static final String STOP_JSON = "{\"action\":\"STOP\"}";
    static final String TEST_JSON = "{\"action\":\"TEST\"}";

    public enum CommandResult { OK, SEND_FAILED, BUSY, DISABLED }

    private final AlarmProperties props;
    private final AlarmScheduleRepository scheduleRepo;
    private final AlarmDecisionService decisions;
    private final AlarmEventRepository eventRepo;
    private final SirenConnectivityEventRepository connRepo;
    private final DeviceRepository deviceRepo;
    private final SirenLink link;
    private final AlarmNotifier notifier;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    private final SirenHealth health;

    // --- trang thai chay, khoa bang this
    private Instant activeUntil;
    private Long openEventId;
    private Boolean lastArmed;
    private boolean armedPublished;
    private Long pendingTestId;
    private Instant testDeadline;
    private boolean pendingTestAuto;

    public AlarmService(AlarmProperties props,
                        AlarmScheduleRepository scheduleRepo,
                        AlarmDecisionService decisions,
                        AlarmEventRepository eventRepo,
                        SirenConnectivityEventRepository connRepo,
                        DeviceRepository deviceRepo,
                        SirenLink link,
                        AlarmNotifier notifier,
                        ObjectMapper objectMapper,
                        Clock clock) {
        this.props = props;
        this.scheduleRepo = scheduleRepo;
        this.decisions = decisions;
        this.eventRepo = eventRepo;
        this.connRepo = connRepo;
        this.deviceRepo = deviceRepo;
        this.link = link;
        this.notifier = notifier;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.health = new SirenHealth(
                Duration.ofSeconds(props.sirenOfflineAfterSeconds()),
                Duration.ofSeconds(props.sirenOfflineGraceSeconds()),
                clock.instant());
    }

    // ================================================================ khoi dong

    /** Khoi phuc dot coi dang keu do lan chay truoc de lai, dong cac dot da het gio. */
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void restore() {
        Instant now = clock.instant();
        List<AlarmEvent> open = eventRepo.findByStoppedAtIsNull().stream()
                .sorted(Comparator.comparing(AlarmEvent::getTriggeredAt))
                .toList();
        for (int i = 0; i < open.size(); i++) {
            AlarmEvent row = open.get(i);
            boolean last = i == open.size() - 1;
            if (last && row.isCommandOk() && row.expectedEnd().isAfter(now)) {
                openEventId = row.getId();
                activeUntil = row.expectedEnd();
                log.info("[alarm] Khoi phuc dot coi #{} keu toi {}", row.getId(), activeUntil);
            } else {
                row.stop(row.expectedEnd(), AlarmEvent.StopSource.TIMEOUT);
                eventRepo.save(row);
            }
        }
    }

    // ================================================================ cua

    @Async("alarmExecutor")
    @EventListener
    public void onDoorTransition(DoorTransitionEvent e) {
        handleDoorTransition(e);
    }

    synchronized void handleDoorTransition(DoorTransitionEvent e) {
        if (!props.enabled() || e.state() != dev.dada.doorsensor.domain.DoorState.OPEN) {
            return;
        }
        Instant now = clock.instant();

        // Mac dinh im lang khi khong chac chan (muc 3.10.5).
        if (e.firstReport()) {
            log.info("[alarm] Bo qua {}: tin dau tien, khong biet cua vua mo hay da mo tu truoc", e.deviceId());
            return;
        }
        if (e.occurredAt().isBefore(now.minus(MAX_EVENT_AGE))) {
            log.warn("[alarm] Bo qua {}: su kien luc {} da qua cu", e.deviceId(), e.occurredAt());
            return;
        }

        AlarmSchedule schedule = scheduleRepo.current();
        boolean active = isActive(now);
        AlarmDecision d = decisions.decide(
                new DoorEvent(e.deviceId(), DoorState.OPEN, e.occurredAt(), true), schedule, active);
        log.info("[alarm] {} mo -> {} ({})", e.deviceId(), d.action(), d.reason());
        if (!d.shouldBeep()) {
            return;
        }

        String sirenId = props.sirenId();
        boolean ok = link.sendCommand(sirenId, beepJson(d));
        Instant until = now.plusMillis(d.durationMs());

        // Cua mo tiep trong luc coi dang keu: firmware dat lai han, may chu keo dai
        // chinh dong dang mo. Khong gui Telegram lan nua, tin dau da du.
        if (active && openEventId != null) {
            if (ok) {
                eventRepo.findById(openEventId).ifPresent(row -> {
                    row.setDurationMs((int) Duration.between(row.getTriggeredAt(), until).toMillis());
                    eventRepo.save(row);
                });
                activeUntil = until;
            } else {
                log.warn("[alarm] Gia han coi that bai, coi se tat theo han cu {}", activeUntil);
            }
            return;
        }

        AlarmEvent row = new AlarmEvent(e.deviceId(), sirenId, e.occurredAt(), now,
                (int) d.durationMs(), d.reason(), ok);
        if (!ok) {
            row.stop(now, null);
        }
        row = eventRepo.save(row);
        if (ok) {
            openEventId = row.getId();
            activeUntil = until;
        }
        log.info("[alarm] Phat lenh coi #{} {} ms, tre tu luc cua mo {} ms, broker {}",
                row.getId(), d.durationMs(), Duration.between(e.occurredAt(), now).toMillis(),
                ok ? "da nhan" : "KHONG nhan");
        notifier.intrusion(e.label(), e.occurredAt(), (int) (d.durationMs() / 1000), ok, health.isOnline());
    }

    // ================================================================ nut bam

    /** Tat tieng coi dang keu (CN-13). Khong doi che do bao ve. */
    public synchronized CommandResult stop() {
        return stopInternal(AlarmEvent.StopSource.REMOTE, true);
    }

    /**
     * Gui lenh thu: coi keu 200 ms (muc 3.10.1).
     *
     * @param auto true neu do lich tu thu goi
     */
    public synchronized CommandResult test(boolean auto) {
        if (!props.enabled()) {
            return CommandResult.DISABLED;
        }
        Instant now = clock.instant();
        // Lenh thu khong duoc chen vao luc coi dang bao dong that.
        if (isActive(now)) {
            return CommandResult.BUSY;
        }
        String sirenId = props.sirenId();
        boolean ok = link.sendCommand(sirenId, TEST_JSON);

        AlarmEvent row = new AlarmEvent(sirenId, sirenId, null, now, TEST_DURATION_MS,
                auto ? "TEST_AUTO" : "TEST", ok);
        row.stop(now.plusMillis(TEST_DURATION_MS), ok ? AlarmEvent.StopSource.TIMEOUT : null);
        row = eventRepo.save(row);

        if (ok) {
            pendingTestId = row.getId();
            testDeadline = now.plusSeconds(props.ackTimeoutSeconds());
            pendingTestAuto = auto;
        } else if (auto) {
            notifier.selfTestFailed("Không gửi được lệnh thử tới broker.");
        }
        return ok ? CommandResult.OK : CommandResult.SEND_FAILED;
    }

    @Scheduled(cron = "${app.alarm.self-test-cron:-}", zone = "Asia/Ho_Chi_Minh")
    public void scheduledSelfTest() {
        synchronized (this) {
            if (!props.enabled()) {
                return;
            }
            // Coi dang mat lien lac thi da co canh bao rieng (CN-16) neu dang bao ve.
            // Thu luc nay chi sinh them mot tin trung y.
            if (!health.isOnline()) {
                log.info("[alarm] Bo qua tu thu: coi dang mat lien lac");
                return;
            }
            CommandResult r = test(true);
            log.info("[alarm] Tu thu coi: {}", r);
        }
    }

    // ================================================================ lich doi

    @Async("alarmExecutor")
    @TransactionalEventListener(fallbackExecution = true)
    public void onScheduleChanged(AlarmMessages.ScheduleChanged m) {
        synchronized (this) {
            // Tat bao ve trong luc coi dang keu thi tat luon coi. Nguoi dung bam
            // "tat bao ve" la da biet chuyen gi dang xay ra.
            if (m.disarmedByUser()) {
                stopInternal(AlarmEvent.StopSource.OVERRIDE, false);
            }
            tick();
        }
    }

    // ================================================================ node coi

    @Async("alarmExecutor")
    @EventListener
    public void onSirenStatus(AlarmMessages.SirenStatus m) {
        handleSirenStatus(m);
    }

    @Async("alarmExecutor")
    @EventListener
    public void onSirenState(AlarmMessages.SirenState m) {
        handleSirenState(m);
    }

    @Async("alarmExecutor")
    @EventListener
    public void onBrokerConnected(AlarmMessages.BrokerConnected m) {
        synchronized (this) {
            // Broker co the da mat thong diep giu lai (xoa du lieu, doi broker).
            // Moi lan noi lai ghi lai cho chac, thong diep giu lai ghi de nen khong hai.
            armedPublished = false;
            publishArmedIfNeeded();
        }
    }

    synchronized void handleSirenStatus(AlarmMessages.SirenStatus m) {
        if (!isOurSiren(m.sirenId())) {
            return;
        }
        String v = m.payload().trim().toLowerCase();
        boolean online;
        if (v.startsWith("online")) {
            online = true;
        } else if (v.startsWith("offline")) {
            online = false;
        } else {
            log.warn("[alarm] Gia tri status la tu {}: {}", m.sirenId(), m.payload());
            return;
        }
        apply(health.onStatus(online, !m.retained(), m.receivedAt()), m.receivedAt());
    }

    synchronized void handleSirenState(AlarmMessages.SirenState m) {
        if (!isOurSiren(m.sirenId())) {
            return;
        }
        // state giu lai van nam tren broker sau khi node chet, khong phai bang
        // chung node dang song, cung khong phai xac nhan cua lenh nao.
        if (m.retained()) {
            return;
        }
        apply(health.onSeen(m.receivedAt()), m.receivedAt());

        if ("ON".equals(parseSirenState(m.payload()))) {
            String sirenId = props.sirenId();
            eventRepo.findFirstBySirenIdAndAckedAtIsNullAndCommandOkTrueAndTriggeredAtAfterOrderByTriggeredAtDesc(
                            sirenId, m.receivedAt().minus(ACK_WINDOW))
                    .ifPresent(row -> {
                        row.setAckedAt(m.receivedAt());
                        eventRepo.save(row);
                        log.info("[alarm] Coi xac nhan lenh #{} sau {} ms", row.getId(),
                                Duration.between(row.getTriggeredAt(), m.receivedAt()).toMillis());
                    });
        }
    }

    // ================================================================ dinh ky

    @Scheduled(fixedDelayString = "${app.alarm.tick-ms:10000}")
    public void scheduledTick() {
        tick();
    }

    synchronized void tick() {
        if (!props.enabled()) {
            return;
        }
        Instant now = clock.instant();

        // 1. Dot coi da het thoi luong: dong lai, nguon dung la TIMEOUT.
        if (openEventId != null && activeUntil != null && !now.isBefore(activeUntil)) {
            Long id = openEventId;
            Instant end = activeUntil;
            eventRepo.findById(id).ifPresent(row -> {
                if (row.getStoppedAt() == null) {
                    row.stop(end, AlarmEvent.StopSource.TIMEOUT);
                    eventRepo.save(row);
                }
            });
            openEventId = null;
            activeUntil = null;
        }

        // 2. Che do bao ve doi theo lich hoac het han thao tac tay.
        boolean armed = decisions.isArmed(scheduleRepo.current());
        if (lastArmed == null || armed != lastArmed) {
            boolean known = lastArmed != null;
            lastArmed = armed;
            armedPublished = false;
            log.info("[alarm] Che do bao ve: {}", armed ? "BAT" : "TAT");
            // Bang 3.4 dong 1: cua mo san luc bat dau thi khong keu, chi nhac.
            // Lan tick dau sau khi khoi dong khong tinh la "vua bat".
            if (known && armed) {
                remindOpenDoors();
            }
        }
        publishArmedIfNeeded();

        // 3. Suc khoe node coi.
        apply(health.tick(armed, now), now);

        // 4. Lenh thu dang cho xac nhan.
        if (pendingTestId != null && now.isAfter(testDeadline)) {
            Long id = pendingTestId;
            boolean auto = pendingTestAuto;
            pendingTestId = null;
            boolean acked = eventRepo.findById(id).map(r -> r.getAckedAt() != null).orElse(false);
            if (acked) {
                log.info("[alarm] Lenh thu #{} da duoc coi xac nhan", id);
            } else {
                log.warn("[alarm] Lenh thu #{} khong duoc coi xac nhan trong {} giay",
                        id, props.ackTimeoutSeconds());
                if (auto) {
                    notifier.selfTestFailed("Còi không xác nhận lệnh thử trong %d giây."
                            .formatted(props.ackTimeoutSeconds()));
                }
            }
        }
    }

    // ================================================================ doc trang thai

    public synchronized AlarmStatusDto status() {
        Instant now = clock.instant();
        boolean armed = decisions.isArmed(scheduleRepo.current());
        boolean active = isActive(now);
        return new AlarmStatusDto(
                props.enabled(),
                armed,
                AlarmStatusDto.level(props.enabled(), armed, health.isOnline()),
                props.sirenId(),
                health.link().name(),
                health.lastSeen(),
                active,
                active ? activeUntil : null);
    }

    // ================================================================ noi bo

    private CommandResult stopInternal(AlarmEvent.StopSource source, boolean evenIfIdle) {
        if (!props.enabled()) {
            return CommandResult.DISABLED;
        }
        Instant now = clock.instant();
        boolean active = isActive(now);
        if (!active && !evenIfIdle) {
            return CommandResult.OK;
        }
        // Gui STOP ca khi may chu nghi coi dang im: nguoi dung nghe thay coi keu
        // thi coi dang keu, trang thai trong bo nho co the sai (vi du sau khoi dong lai).
        boolean ok = link.sendCommand(props.sirenId(), STOP_JSON);
        if (!ok) {
            log.warn("[alarm] Gui STOP that bai, coi se tu tat luc {}", activeUntil);
            return CommandResult.SEND_FAILED;
        }
        if (active && openEventId != null) {
            eventRepo.findById(openEventId).ifPresent(row -> {
                row.stop(now, source);
                eventRepo.save(row);
            });
        }
        openEventId = null;
        activeUntil = null;
        log.info("[alarm] Da tat coi ({})", source);
        return CommandResult.OK;
    }

    private void apply(SirenHealth.Result r, Instant at) {
        if (r.hasChange()) {
            connRepo.save(new SirenConnectivityEvent(props.sirenId(), r.change().online(), r.change().source(), at));
            log.info("[alarm] Coi {} -> {} ({})", props.sirenId(),
                    r.change().online() ? "online" : "offline", r.change().source());
        }
        switch (r.alert()) {
            case OFFLINE_WHILE_ARMED -> {
                log.warn("[alarm] Dang bao ve nhung coi mat lien lac, gui canh bao");
                notifier.sirenOffline(health.lastSeen());
            }
            case RECONNECTED -> notifier.sirenBack();
            case NONE -> { }
        }
    }

    private void publishArmedIfNeeded() {
        if (!armedPublished && lastArmed != null) {
            armedPublished = link.publishArmed(lastArmed);
        }
    }

    private void remindOpenDoors() {
        List<String> open = deviceRepo.findAllByOrderByLabelAsc().stream()
                .filter(d -> d.getLastState() == dev.dada.doorsensor.domain.DoorState.OPEN)
                .map(Device::getLabel)
                .toList();
        if (!open.isEmpty()) {
            log.info("[alarm] Bat dau bao ve khi cua dang mo: {}", open);
            notifier.openAtArmStart(open);
        }
    }

    private boolean isActive(Instant now) {
        return activeUntil != null && now.isBefore(activeUntil);
    }

    private boolean isOurSiren(String sirenId) {
        if (!props.enabled()) {
            return false;
        }
        if (!props.sirenId().equals(sirenId)) {
            log.debug("[alarm] Bo qua tin tu node coi la: {}", sirenId);
            return false;
        }
        return true;
    }

    static String beepJson(AlarmDecision d) {
        return "{\"action\":\"BEEP\",\"duration_ms\":%d,\"reason\":\"%s\"}"
                .formatted(d.durationMs(), d.reason());
    }

    /** Chap nhan {"state":"ON"}, {"siren":"ON"} hoac chuoi tran "ON". */
    String parseSirenState(String payload) {
        String p = payload == null ? "" : payload.trim();
        try {
            if (p.startsWith("{")) {
                JsonNode n = objectMapper.readTree(p);
                JsonNode v = n.hasNonNull("state") ? n.get("state") : n.get("siren");
                return v == null ? null : v.asText().trim().toUpperCase();
            }
        } catch (Exception e) {
            log.debug("[alarm] state khong phai JSON: {}", p);
            return null;
        }
        return p.toUpperCase();
    }
}
