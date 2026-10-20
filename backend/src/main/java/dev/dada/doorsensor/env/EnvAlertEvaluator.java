package dev.dada.doorsensor.env;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * May trang thai canh bao nguong cho mot dai luong cua mot thiet bi. Logic thuan,
 * khong phu thuoc Spring, khong goi mang, de kiem thu bang thoi gian gia.
 *
 * <pre>
 *            vuot nguong                 vuot lien tuc &gt;= hold
 *   NORMAL ─────────────▶ PENDING ──────────────────────────▶ ALERTED  (bao RAISED)
 *      ▲                    │ ve trong nguong                    │
 *      └────────────────────┘                                    │ ve qua nguong
 *      ▲                                                         │ mot khoang tre
 *      └─────────────────────────────────────────────────────────┘ (bao CLEARED)
 * </pre>
 *
 * <p><b>Hai lop chong bao nham.</b> Thoi gian giu (hold): mot mau vuot nguong
 * chua bao, phai vuot lien tuc chung do phut. Khoang tre (hysteresis): da bao
 * 35 do thi phai xuong toi 34,5 do moi coi la het, neu khong gia tri lang vang
 * quanh 35 se ban mot chuoi tin "vuot" roi "het" xen ke.
 *
 * <p>Khong co cooldown rieng: mot canh bao chi ket thuc khi gia tri that su ve
 * lai, nen khong the lap lai lien tuc ma khong co dao dong that cua moi truong.
 */
public final class EnvAlertEvaluator {

    public enum Metric { TEMP, RH }

    public enum Direction { HIGH, LOW }

    /** Khoang tre mac dinh: 0,5 do C, 3 % do am. Lon hon nhieu sai so cua AHT20. */
    public static final double TEMP_HYSTERESIS = 0.5;
    public static final double RH_HYSTERESIS = 3.0;

    public static double hysteresis(Metric m) {
        return m == Metric.TEMP ? TEMP_HYSTERESIS : RH_HYSTERESIS;
    }

    /** Nguong cua mot dai luong. null o chieu nao la khong canh bao chieu do. */
    public record Limits(Double high, Double low) {
        static final Limits NONE = new Limits(null, null);
    }

    /** Mot lan doi trang thai can ghi nhat ky va bao Telegram. */
    public record Transition(String deviceId, Metric metric, Direction direction, boolean raised,
                             double value, Double threshold, Instant at, Instant since, double peak) {
    }

    /** Canh bao dang mo, cho API va Telegram. */
    public record Active(String deviceId, Metric metric, Direction direction, Instant since,
                         double value, Double threshold, double peak) {
    }

    private enum Phase { PENDING, ALERTED }

    private static final class State {
        Phase phase;
        Direction direction;
        Instant since;
        double last;
        double peak;
        Double threshold;
    }

    private record Key(String deviceId, Metric metric) {
    }

    private final Map<Key, State> states = new HashMap<>();

    /**
     * Xet mot mau. Tra ve thay doi can bao (vua vuot du lau, hoac vua het), neu co.
     *
     * @param value  gia tri do, null thi bo qua (node chi gui mot trong hai dai luong)
     * @param limits nguong hien hanh
     * @param hold   thoi gian phai vuot lien tuc truoc khi bao
     */
    public synchronized Optional<Transition> evaluate(String deviceId, Metric metric, Double value,
                                                      Limits limits, Duration hold, Instant now) {
        if (value == null || value.isNaN()) {
            return Optional.empty();
        }
        Key key = new Key(deviceId, metric);
        State s = states.get(key);
        Direction breach = breach(value, limits);

        if (s != null && s.phase == Phase.ALERTED) {
            s.last = value;
            if (s.direction == Direction.HIGH) {
                s.peak = Math.max(s.peak, value);
            } else {
                s.peak = Math.min(s.peak, value);
            }
            if (!cleared(s.direction, value, limits, hysteresis(metric))) {
                return Optional.empty();
            }
            states.remove(key);
            Transition t = new Transition(deviceId, metric, s.direction, false, value,
                    threshold(s.direction, limits, s.threshold), now, s.since, s.peak);
            // Vua het chieu nay ma da vuot chieu kia (doi nguong tren giao dien): bat dau dem lai.
            if (breach != null && breach != s.direction) {
                startPending(key, breach, value, limits, now);
            }
            return Optional.of(t);
        }

        if (breach == null) {
            states.remove(key);
            return Optional.empty();
        }

        if (s == null || s.direction != breach) {
            s = startPending(key, breach, value, limits, now);
        } else {
            s.last = value;
            s.threshold = threshold(breach, limits, s.threshold);
            s.peak = breach == Direction.HIGH ? Math.max(s.peak, value) : Math.min(s.peak, value);
        }

        if (Duration.between(s.since, now).compareTo(hold) >= 0) {
            s.phase = Phase.ALERTED;
            return Optional.of(new Transition(deviceId, metric, breach, true, value, s.threshold,
                    now, s.since, s.peak));
        }
        return Optional.empty();
    }

    /** Khoi phuc canh bao dang mo tu nhat ky sau khi khoi dong lai, de khong bao lai lan nua. */
    public synchronized void restore(String deviceId, Metric metric, Direction direction, Instant since,
                                     double value, Double threshold) {
        State s = new State();
        s.phase = Phase.ALERTED;
        s.direction = direction;
        s.since = since;
        s.last = value;
        s.peak = value;
        s.threshold = threshold;
        states.put(new Key(deviceId, metric), s);
    }

    /** Tat canh bao: quen moi trang thai, khong bao "het" cho canh bao dang mo. */
    public synchronized void reset() {
        states.clear();
    }

    public synchronized List<Active> active() {
        List<Active> out = new ArrayList<>();
        states.forEach((k, s) -> {
            if (s.phase == Phase.ALERTED) {
                out.add(new Active(k.deviceId(), k.metric(), s.direction, s.since, s.last, s.threshold, s.peak));
            }
        });
        out.sort(Comparator.comparing(Active::since));
        return out;
    }

    // ------------------------------------------------------------------

    private State startPending(Key key, Direction d, double value, Limits limits, Instant now) {
        State s = new State();
        s.phase = Phase.PENDING;
        s.direction = d;
        s.since = now;
        s.last = value;
        s.peak = value;
        s.threshold = threshold(d, limits, null);
        states.put(key, s);
        return s;
    }

    static Direction breach(double value, Limits l) {
        if (l.high() != null && value > l.high()) {
            return Direction.HIGH;
        }
        if (l.low() != null && value < l.low()) {
            return Direction.LOW;
        }
        return null;
    }

    /** Nguong bi bo (null) cung tinh la het: nguoi dung vua tat chieu canh bao do. */
    static boolean cleared(Direction d, double value, Limits l, double hyst) {
        if (d == Direction.HIGH) {
            return l.high() == null || value <= l.high() - hyst;
        }
        return l.low() == null || value >= l.low() + hyst;
    }

    private static Double threshold(Direction d, Limits l, Double fallback) {
        Double t = d == Direction.HIGH ? l.high() : l.low();
        return t != null ? t : fallback;
    }
}
