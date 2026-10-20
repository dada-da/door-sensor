import { useCallback, useEffect, useState, type ChangeEvent } from 'react';
import { api } from '../api';
import { clockTime, dateTime, duration } from '../format';
import type {
  EnvAlertActive,
  EnvAlertEventRow,
  EnvAlertRule,
  EnvAlerts,
  EnvLatest,
  EnvMetric,
  EnvPoint,
} from '../types';
import { Sparkline } from './Sparkline';
import { RH_COLOR, TEMP_COLOR } from './stats/chartKit';

/** Node gui moi 60 giay: hoi so moi nhat va canh bao moi 30 giay, duong 24 gio moi 5 phut. */
const NOW_MS = 30_000;
const SERIES_MS = 5 * 60_000;
const STALE_S = 300;

const fmt = (v: number | null | undefined, digits = 1) =>
  v == null ? '--' : v.toFixed(digits).replace('.', ',');

interface MetricSpec {
  key: EnvMetric;
  label: string;
  unit: string;
  min: number;
  max: number;
  color: string;
}

const TEMP: MetricSpec = { key: 'TEMP', label: 'Nhiệt độ', unit: '°C', min: 10, max: 45, color: TEMP_COLOR };
const RH: MetricSpec = { key: 'RH', label: 'Độ ẩm', unit: '%', min: 0, max: 100, color: RH_COLOR };

/**
 * Nhiet do, do am hien tai kem nguong canh bao, thay hai dong ho nua vong cu.
 * Thanh ngang co dai an toan giua hai nguong: nhin vi tri vach la biet con xa
 * nguong bao nhieu, khong can doc so.
 */
export function EnvCard() {
  const [latest, setLatest] = useState<EnvLatest | null>(null);
  const [points, setPoints] = useState<EnvPoint[]>([]);
  const [alerts, setAlerts] = useState<EnvAlerts | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadNow = useCallback(async () => {
    try {
      const [l, a] = await Promise.all([api.envLatest(), api.envAlerts()]);
      setLatest(l[0] ?? null);
      setAlerts(a);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Không tải được số đo');
    }
  }, []);

  useEffect(() => {
    void loadNow();
    const t = window.setInterval(() => void loadNow(), NOW_MS);
    return () => window.clearInterval(t);
  }, [loadNow]);

  useEffect(() => {
    const load = () =>
      api
        .envSeries(24)
        .then((s) => setPoints(s.points))
        .catch(() => undefined);
    void load();
    const t = window.setInterval(load, SERIES_MS);
    return () => window.clearInterval(t);
  }, []);

  const rule = alerts?.rule ?? null;
  const active = alerts?.active ?? [];
  const stale = latest != null && latest.ageSeconds > STALE_S;

  return (
    <article className={`card env-card${active.length ? ' has-alert' : ''}`} aria-label="Nhiệt độ và độ ẩm">
      <header className="card-head">
        <div>
          <h2 className="card-title">Nhiệt độ, độ ẩm</h2>
          <span className="card-sub">
            AHT20{latest ? ` · ${latest.deviceId}` : ''}
            {latest && ` · đo lúc ${clockTime(latest.measuredAt)}`}
          </span>
        </div>
        <span className={`door-rule-badge${rule?.enabled ? ' is-armed' : ''}`}>
          {rule == null ? '--' : rule.enabled ? 'Cảnh báo bật' : 'Cảnh báo tắt'}
        </span>
      </header>

      {error && !latest && <p className="log-empty">Không tải được số đo: {error}</p>}
      {!error && !latest && <p className="log-empty">Chưa nhận được số đo nào từ AHT20.</p>}
      {stale && (
        <p className="env-stale">Số đo đã cũ {duration(latest!.ageSeconds)}. Node còi có thể mất kết nối.</p>
      )}

      {active.map((a) => (
        <ActiveAlert key={`${a.deviceId}-${a.metric}`} a={a} />
      ))}

      {latest && (
        <>
          <Metric
            spec={TEMP}
            value={latest.temperatureC}
            low={rule?.enabled ? rule.tempLow : null}
            high={rule?.enabled ? rule.tempHigh : null}
            alert={active.some((a) => a.metric === 'TEMP')}
            series={points.map((p) => p.tempAvg)}
            min24={min(points.map((p) => p.tempMin))}
            max24={max(points.map((p) => p.tempMax))}
          />
          <Metric
            spec={RH}
            value={latest.humidityPct}
            low={rule?.enabled ? rule.rhLow : null}
            high={rule?.enabled ? rule.rhHigh : null}
            alert={active.some((a) => a.metric === 'RH')}
            series={points.map((p) => p.rhAvg)}
            min24={min(points.map((p) => p.rhMin))}
            max24={max(points.map((p) => p.rhMax))}
          />
        </>
      )}

      {alerts && <AlertSettings alerts={alerts} onSaved={setAlerts} />}

      <p className="card-foot">
        <a href="#thong-ke">Biểu đồ theo thời gian</a> · Telegram: <code>/nhietdo</code>
      </p>
    </article>
  );
}

function min(xs: Array<number | null>): number | null {
  const v = xs.filter((x): x is number => x != null);
  return v.length ? Math.min(...v) : null;
}

function max(xs: Array<number | null>): number | null {
  const v = xs.filter((x): x is number => x != null);
  return v.length ? Math.max(...v) : null;
}

// ---------------------------------------------------------------- mot dai luong

function Metric({
  spec,
  value,
  low,
  high,
  alert,
  series,
  min24,
  max24,
}: {
  spec: MetricSpec;
  value: number | null;
  low: number | null;
  high: number | null;
  alert: boolean;
  series: Array<number | null>;
  min24: number | null;
  max24: number | null;
}) {
  return (
    <div className={`env-metric${alert ? ' is-alert' : ''}`}>
      <div className="env-metric-top">
        <span className="env-label">{spec.label}</span>
        <span className="env-value" style={{ color: alert ? undefined : spec.color }}>
          {fmt(value)}
          <small> {spec.unit}</small>
        </span>
        <span className="env-range">
          24 giờ: {fmt(min24)}–{fmt(max24)} {spec.unit}
        </span>
      </div>
      <RangeMeter spec={spec} value={value} low={low} high={high} alert={alert} />
      <Sparkline
        values={series}
        color={spec.color}
        height={28}
        label={`${spec.label} 24 giờ qua`}
      />
    </div>
  );
}

/**
 * Thang ngang tu min toi max cua dai luong. Phan giua hai nguong to nhat (an
 * toan), vach dung la gia tri hien tai. Khong co nguong thi chi con vach.
 */
function RangeMeter({
  spec,
  value,
  low,
  high,
  alert,
}: {
  spec: MetricSpec;
  value: number | null;
  low: number | null;
  high: number | null;
  alert: boolean;
}) {
  const pos = (v: number) => `${Math.min(100, Math.max(0, ((v - spec.min) / (spec.max - spec.min)) * 100))}%`;
  const bandLeft = low != null ? pos(low) : '0%';
  const bandRight = high != null ? pos(high) : '100%';
  const hasBand = low != null || high != null;

  return (
    <div className="meter" aria-hidden="true">
      <div className="meter-track">
        {hasBand && (
          <div
            className="meter-band"
            style={{ left: bandLeft, width: `calc(${bandRight} - ${bandLeft})` }}
          />
        )}
        {low != null && <span className="meter-thr" style={{ left: pos(low) }} />}
        {high != null && <span className="meter-thr" style={{ left: pos(high) }} />}
        {value != null && (
          <span
            className={`meter-mark${alert ? ' is-alert' : ''}`}
            style={{ left: pos(value), background: alert ? undefined : spec.color }}
          />
        )}
      </div>
      {/* Chi ghi so o hai nguong: dau thang (10, 45 do) khong mang y nghia gi. */}
      <div className="meter-scale">
        {low != null && (
          <span className="meter-thr-label" style={{ left: pos(low) }}>
            {fmt(low, 0)}
          </span>
        )}
        {high != null && (
          <span className="meter-thr-label" style={{ left: pos(high) }}>
            {fmt(high, 0)}
          </span>
        )}
      </div>
    </div>
  );
}

function ActiveAlert({ a }: { a: EnvAlertActive }) {
  const name = a.metric === 'TEMP' ? 'Nhiệt độ' : 'Độ ẩm';
  const unit = a.metric === 'TEMP' ? ' °C' : ' %';
  const dir = a.direction === 'HIGH' ? 'cao' : 'thấp';
  const peak = a.direction === 'HIGH' ? 'cao nhất' : 'thấp nhất';
  return (
    <p className="env-alert" role="alert">
      <strong>
        {name} {dir}
      </strong>{' '}
      từ {clockTime(a.since)}, ngưỡng {fmt(a.threshold)}
      {unit}, {peak} {fmt(a.peak)}
      {unit}
    </p>
  );
}

// ---------------------------------------------------------------- cai nguong

interface Draft {
  enabled: boolean;
  tempHigh: string;
  tempLow: string;
  rhHigh: string;
  rhLow: string;
  holdMinutes: string;
}

const s = (v: number | null) => (v == null ? '' : String(v));

function toDraft(r: EnvAlertRule): Draft {
  return {
    enabled: r.enabled,
    tempHigh: s(r.tempHigh),
    tempLow: s(r.tempLow),
    rhHigh: s(r.rhHigh),
    rhLow: s(r.rhLow),
    holdMinutes: String(r.holdMinutes),
  };
}

/** Chuoi rong la "khong dat nguong". Tra ve undefined neu khong phai so. */
function num(text: string): number | null | undefined {
  const t = text.trim().replace(',', '.');
  if (t === '') return null;
  const n = Number(t);
  return Number.isFinite(n) ? n : undefined;
}

function validate(d: Draft, tH: number, rH: number): { rule?: EnvAlertRule; problem?: string } {
  const tempHigh = num(d.tempHigh);
  const tempLow = num(d.tempLow);
  const rhHigh = num(d.rhHigh);
  const rhLow = num(d.rhLow);
  const hold = Number(d.holdMinutes);
  if ([tempHigh, tempLow, rhHigh, rhLow].some((v) => v === undefined)) return { problem: 'Ngưỡng phải là số' };
  if ([tempHigh, tempLow].some((v) => v != null && (v < -40 || v > 85)))
    return { problem: 'Nhiệt độ từ -40 đến 85 °C (dải đo AHT20)' };
  if ([rhHigh, rhLow].some((v) => v != null && (v < 0 || v > 100))) return { problem: 'Độ ẩm từ 0 đến 100 %' };
  if (tempHigh != null && tempLow != null && tempLow > tempHigh - 2 * tH)
    return { problem: `Ngưỡng nhiệt thấp phải nhỏ hơn ngưỡng cao ít nhất ${2 * tH} °C` };
  if (rhHigh != null && rhLow != null && rhLow > rhHigh - 2 * rH)
    return { problem: `Ngưỡng ẩm thấp phải nhỏ hơn ngưỡng cao ít nhất ${2 * rH} %` };
  if (!Number.isInteger(hold) || hold < 0 || hold > 120) return { problem: 'Thời gian giữ từ 0 đến 120 phút' };
  return {
    rule: {
      enabled: d.enabled,
      tempHigh: tempHigh ?? null,
      tempLow: tempLow ?? null,
      rhHigh: rhHigh ?? null,
      rhLow: rhLow ?? null,
      holdMinutes: hold,
    },
  };
}

function AlertSettings({ alerts, onSaved }: { alerts: EnvAlerts; onSaved: (a: EnvAlerts) => void }) {
  const saved = toDraft(alerts.rule);
  const [draft, setDraft] = useState<Draft>(saved);
  const [message, setMessage] = useState<{ text: string; bad: boolean } | null>(null);
  const [events, setEvents] = useState<EnvAlertEventRow[] | null>(null);
  const [open, setOpen] = useState(false);
  const dirty = JSON.stringify(draft) !== JSON.stringify(saved);

  // Dong bo lai sau moi lan hoi, tru khi dang sua do.
  useEffect(() => {
    if (!dirty) setDraft(toDraft(alerts.rule));
  }, [alerts.rule]);

  useEffect(() => {
    if (!open) return;
    api
      .envAlertEvents(8)
      .then(setEvents)
      .catch(() => setEvents([]));
  }, [open, alerts.active.length]);

  const { rule, problem } = validate(draft, alerts.tempHysteresis, alerts.rhHysteresis);
  const set = (k: keyof Draft) => (e: ChangeEvent<HTMLInputElement>) =>
    setDraft((d) => ({ ...d, [k]: k === 'enabled' ? e.target.checked : e.target.value }));

  const save = async () => {
    if (!rule) return;
    try {
      onSaved(await api.saveEnvAlerts(rule));
      setMessage({ text: 'Đã lưu ngưỡng cảnh báo', bad: false });
    } catch (e) {
      setMessage({ text: e instanceof Error ? e.message : 'Lưu thất bại', bad: true });
    }
  };

  return (
    <details className="env-settings" onToggle={(e) => setOpen((e.target as HTMLDetailsElement).open)}>
      <summary>Ngưỡng cảnh báo Telegram</summary>
      <label className="env-enable">
        <input type="checkbox" checked={draft.enabled} onChange={set('enabled')} /> Bật cảnh báo nhiệt độ, độ ẩm
      </label>
      <div className="env-grid">
        <span />
        <span className="env-grid-head">Thấp dưới</span>
        <span className="env-grid-head">Cao trên</span>
        <span className="schedule-key">Nhiệt độ (°C)</span>
        <input inputMode="decimal" value={draft.tempLow} onChange={set('tempLow')} placeholder="tắt" aria-label="Ngưỡng nhiệt độ thấp" />
        <input inputMode="decimal" value={draft.tempHigh} onChange={set('tempHigh')} placeholder="tắt" aria-label="Ngưỡng nhiệt độ cao" />
        <span className="schedule-key">Độ ẩm (%)</span>
        <input inputMode="decimal" value={draft.rhLow} onChange={set('rhLow')} placeholder="tắt" aria-label="Ngưỡng độ ẩm thấp" />
        <input inputMode="decimal" value={draft.rhHigh} onChange={set('rhHigh')} placeholder="tắt" aria-label="Ngưỡng độ ẩm cao" />
      </div>
      <label className="env-hold">
        Vượt liên tục
        <input inputMode="numeric" value={draft.holdMinutes} onChange={set('holdMinutes')} aria-label="Số phút giữ" />
        phút mới báo
      </label>
      <p className="door-rule-note">
        Để trống là không cảnh báo chiều đó. Hết cảnh báo khi giá trị lùi qua ngưỡng {fmt(alerts.tempHysteresis)} °C
        hoặc {fmt(alerts.rhHysteresis, 0)} %, để giá trị lảng vảng quanh ngưỡng không gửi tin liên tục.
      </p>
      {problem && <p className="door-rule-note is-alert-text">{problem}</p>}
      <div className="door-rule-actions">
        <button className="btn btn-small" disabled={!dirty || !rule} onClick={() => void save()}>
          Lưu ngưỡng
        </button>
        {dirty && (
          <button className="btn btn-small btn-ghost" onClick={() => setDraft(saved)}>
            Hủy
          </button>
        )}
      </div>
      {message && <p className={`alarm-message${message.bad ? ' is-bad' : ''}`}>{message.text}</p>}

      <h3 className="alarm-subtitle">Lần cảnh báo gần đây</h3>
      {events == null && <p className="door-rule-note">Đang tải…</p>}
      {events?.length === 0 && <p className="door-rule-note">Chưa có lần vượt ngưỡng nào.</p>}
      {events && events.length > 0 && (
        <ul className="env-events">
          {events.map((e) => (
            <li key={e.id} className={e.kind === 'RAISED' ? 'is-raised' : undefined}>
              <span className="env-events-time">{dateTime(e.occurredAt)}</span>
              {e.metric === 'TEMP' ? 'Nhiệt độ' : 'Độ ẩm'} {e.direction === 'HIGH' ? 'cao' : 'thấp'}{' '}
              {e.kind === 'RAISED' ? 'vượt' : 'hết, đỉnh'} {fmt(e.value)}
              {e.metric === 'TEMP' ? ' °C' : ' %'}
            </li>
          ))}
        </ul>
      )}
    </details>
  );
}
