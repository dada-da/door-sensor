import { useEffect, useState } from 'react';
import { api } from '../api';
import { clockTime, dateTime } from '../format';
import type { AlarmLevel, AlarmSettings, AlarmStatus, DoorRule, DoorRuleUpdate } from '../types';

type Run = (action: () => Promise<unknown>) => Promise<string | null>;

interface Props {
  status: AlarmStatus | null;
  doors: DoorRule[];
  settings: AlarmSettings | null;
  error: string | null;
  run: Run;
}

/**
 * Bang dieu khien bao dong (bao cao muc 3.7).
 *
 * Ba trang thai phan biet: dang bao ve, khong bao ve, va dang bao ve nhung coi
 * mat lien lac. Trang thai thu ba to dac mau canh bao, vi la tinh huong nguy hiem
 * nhat ma nguoi dung de hieu nham la an toan.
 */
export function AlarmPanel({ status, doors, settings, error, run }: Props) {
  const [message, setMessage] = useState<{ text: string; bad: boolean } | null>(null);

  const act = async (action: () => Promise<unknown>, ok: string) => {
    const err = await run(action);
    setMessage(err ? { text: err, bad: true } : { text: ok, bad: false });
  };

  return (
    <section className="section">
      <h2 className="section-title">Báo động</h2>
      <p className="section-note">
        Mỗi cửa có khung giờ bảo vệ riêng. Trong khung giờ, cửa được bật báo động mà mở thì còi kêu.
        Tin Telegram mở cửa trong khung giờ được ghi là ngoài giờ thường lệ.
      </p>

      {error && <p className="notice"><span className="notice-strong">{error}</span></p>}

      {status && <Banner status={status} />}

      {status?.sirenActive && (
        <div className="alarm-ringing" role="alert">
          <span>
            Còi đang kêu, tự tắt lúc <strong>{clockTime(status.sirenActiveUntil)}</strong>
          </span>
          <button className="btn btn-danger" onClick={() => act(api.alarmStop, 'Đã gửi lệnh tắt còi')}>
            Tắt còi
          </button>
        </div>
      )}

      <div className="alarm-controls">
        <button
          className="btn"
          onClick={() => act(api.alarmStop, 'Đã gửi lệnh tắt còi')}
          disabled={!status?.enabled}
        >
          Tắt còi
        </button>
        <button
          className="btn"
          onClick={() => act(api.alarmTest, 'Đã gửi lệnh thử, còi kêu 200 ms')}
          disabled={!status?.enabled}
        >
          Thử còi
        </button>
        {settings && (
          <SirenSeconds
            value={settings.sirenSeconds}
            onSave={(v) => act(() => api.saveAlarmSettings({ sirenSeconds: v }), `Còi sẽ kêu ${v} giây`)}
          />
        )}
      </div>

      {message && (
        <p className={`alarm-message${message.bad ? ' is-bad' : ''}`} role="status">
          {message.text}
        </p>
      )}

      <h3 className="alarm-subtitle">Từng cửa</h3>
      {doors.length === 0 && <p className="log-empty">Chưa có cửa nào gửi dữ liệu.</p>}
      <div className="door-rules">
        {doors.map((d) => (
          <DoorRuleCard key={d.deviceId} rule={d} act={act} />
        ))}
      </div>
    </section>
  );
}

// ---------------------------------------------------------------- tong quan

const LEVEL_TEXT: Record<AlarmLevel, string> = {
  ARMED: 'Đang bảo vệ',
  DISARMED: 'Không bảo vệ',
  ARMED_SIREN_OFFLINE: 'Đang bảo vệ, còi mất liên lạc',
  DISABLED: 'Báo động tắt trong cấu hình',
};

function Banner({ status }: { status: AlarmStatus }) {
  const siren = {
    ONLINE: 'trực tuyến',
    OFFLINE: 'mất liên lạc',
    UNKNOWN: 'chưa nhận được tin',
  }[status.sirenLink];

  return (
    <div className={`alarm-banner is-${status.level.toLowerCase().replace(/_/g, '-')}`}>
      <span className="alarm-banner-state">{LEVEL_TEXT[status.level]}</span>
      <span className="alarm-banner-sub">
        Còi {status.sirenId}: {siren}
        {status.sirenLastSeen && <> &nbsp;·&nbsp; tin gần nhất {clockTime(status.sirenLastSeen)}</>}
      </span>
      {status.level === 'ARMED_SIREN_OFFLINE' && (
        <span className="alarm-banner-sub">
          Hệ thống đang bảo vệ nhưng còi sẽ không kêu được. Kiểm tra nguồn và Wi-Fi của node còi.
        </span>
      )}
    </div>
  );
}

function SirenSeconds({ value, onSave }: { value: number; onSave: (v: number) => void }) {
  const [draft, setDraft] = useState(String(value));
  useEffect(() => setDraft(String(value)), [value]);

  const n = Number(draft);
  const valid = Number.isInteger(n) && n >= 1 && n <= 120;
  const dirty = valid && n !== value;

  return (
    <label className="alarm-seconds">
      Thời lượng còi
      <input
        type="number"
        min={1}
        max={120}
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        aria-invalid={!valid}
      />
      giây
      <button className="btn btn-small" disabled={!dirty} onClick={() => onSave(n)}>
        Lưu
      </button>
      {!valid && <span className="is-alert-text">1 đến 120 giây</span>}
    </label>
  );
}

// ---------------------------------------------------------------- tung cua

const DAYS: Array<[number, string]> = [
  [1, 'T2'],
  [2, 'T3'],
  [3, 'T4'],
  [4, 'T5'],
  [5, 'T6'],
  [6, 'T7'],
  [7, 'CN'],
];

const hhmm = (t: string) => t.slice(0, 5);

interface Draft {
  sirenEnabled: boolean;
  startTime: string;
  endTime: string;
  days: number[];
}

function fromRule(r: DoorRule): Draft {
  return {
    sirenEnabled: r.sirenEnabled,
    startTime: hhmm(r.startTime),
    endTime: hhmm(r.endTime),
    days: [...r.days].sort(),
  };
}

function sameDraft(a: Draft, b: Draft): boolean {
  return (
    a.sirenEnabled === b.sirenEnabled &&
    a.startTime === b.startTime &&
    a.endTime === b.endTime &&
    a.days.join(',') === b.days.join(',')
  );
}

function DoorRuleCard({
  rule,
  act,
}: {
  rule: DoorRule;
  act: (action: () => Promise<unknown>, ok: string) => Promise<void>;
}) {
  const saved = fromRule(rule);
  const [draft, setDraft] = useState<Draft>(saved);
  const dirty = !sameDraft(draft, saved);

  // Dong bo lai sau moi lan hoi (5 giay), tru khi nguoi dung dang sua do.
  useEffect(() => {
    if (!dirty) setDraft(fromRule(rule));
  }, [rule]);

  const save = () => {
    const u: DoorRuleUpdate = { ...draft };
    void act(() => api.saveDoorRule(rule.deviceId, u), `Đã lưu ${rule.label}`);
  };

  const wraps = draft.startTime > draft.endTime;
  const empty = draft.startTime === draft.endTime;
  const toggleDay = (d: number) =>
    setDraft((x) => ({
      ...x,
      days: x.days.includes(d) ? x.days.filter((y) => y !== d) : [...x.days, d].sort(),
    }));

  return (
    <article className={`door-rule${rule.armedNow ? ' is-armed' : ''}`}>
      <header className="door-rule-head">
        <div>
          <h4 className="door-rule-name">{rule.label}</h4>
          <span className="door-rule-id">
            {rule.deviceId} &nbsp;·&nbsp; {doorText(rule)}
          </span>
        </div>
        <span className={`door-rule-badge${rule.armedNow ? ' is-armed' : ''}`}>
          {rule.armedNow ? 'Đang bảo vệ' : 'Không bảo vệ'}
        </span>
      </header>

      <label className="door-rule-switch">
        <input
          type="checkbox"
          checked={draft.sirenEnabled}
          onChange={(e) => setDraft((x) => ({ ...x, sirenEnabled: e.target.checked }))}
        />
        <span>Bật báo động: còi kêu khi cửa này mở trong khung giờ</span>
      </label>

      <div className="door-rule-row">
        <span className="schedule-key">Khung giờ</span>
        <span className="time-range">
        <input
          type="time"
          value={draft.startTime}
          onChange={(e) => setDraft((x) => ({ ...x, startTime: e.target.value }))}
          aria-label={`Giờ bắt đầu, ${rule.label}`}
        />
        <span aria-hidden="true">đến</span>
        <input
          type="time"
          value={draft.endTime}
          onChange={(e) => setDraft((x) => ({ ...x, endTime: e.target.value }))}
          aria-label={`Giờ kết thúc, ${rule.label}`}
        />
        </span>
      </div>
      {wraps && <p className="door-rule-note">Khung giờ vắt qua nửa đêm, kết thúc vào sáng hôm sau.</p>}
      {empty && (
        <p className="door-rule-note is-alert-text">Giờ bắt đầu bằng giờ kết thúc: khung rỗng, không bao giờ bảo vệ.</p>
      )}

      <div className="door-rule-row">
        <span className="schedule-key">Ngày</span>
        <div className="day-chips" role="group" aria-label="Ngày khung giờ bắt đầu">
          {DAYS.map(([n, label]) => (
            <button
              key={n}
              type="button"
              className={`day-chip${draft.days.includes(n) ? ' is-on' : ''}`}
              aria-pressed={draft.days.includes(n)}
              onClick={() => toggleDay(n)}
            >
              {label}
            </button>
          ))}
        </div>
      </div>
      {wraps && draft.days.length < 7 && (
        <p className="door-rule-note">Ngày là ngày khung giờ bắt đầu: khung T2 22:00 kéo sang sáng T3.</p>
      )}

      <div className="door-rule-actions">
        <button className="btn btn-small" disabled={!dirty} onClick={save}>
          Lưu
        </button>
        {dirty && (
          <button className="btn btn-small btn-ghost" onClick={() => setDraft(saved)}>
            Hủy
          </button>
        )}
      </div>

      <div className="door-rule-override">
        <span className="schedule-key">Tạm thời</span>
        <div className="door-rule-buttons">
          <button
            className={`btn btn-small${rule.overrideMode === 'ARMED' ? ' is-on' : ''}`}
            onClick={() => act(() => api.doorOverride(rule.deviceId, 'arm'), `${rule.label}: bảo vệ ngay`)}
          >
            Bảo vệ ngay
          </button>
          <button
            className={`btn btn-small${rule.overrideMode === 'DISARMED' ? ' is-on' : ''}`}
            onClick={() => act(() => api.doorOverride(rule.deviceId, 'disarm'), `${rule.label}: tạm tắt`)}
          >
            Tạm tắt
          </button>
          <button
            className={`btn btn-small${rule.overrideMode === 'NONE' ? ' is-on' : ''}`}
            onClick={() => act(() => api.doorOverride(rule.deviceId, 'auto'), `${rule.label}: theo lịch`)}
          >
            Theo lịch
          </button>
        </div>
      </div>
      <p className="door-rule-note">{overrideText(rule)}</p>
    </article>
  );
}

function doorText(r: DoorRule): string {
  if (!r.online) return 'mất liên lạc';
  if (r.doorState === 'OPEN') return 'đang mở';
  if (r.doorState === 'CLOSED') return 'đang đóng';
  return 'chưa rõ';
}

function overrideText(r: DoorRule): string {
  if (!r.sirenEnabled) return 'Báo động của cửa này đang tắt, còi không kêu dù trong khung giờ.';
  const until = r.overrideUntil ? dateTime(r.overrideUntil) : null;
  const expired = r.overrideUntil ? new Date(r.overrideUntil).getTime() < Date.now() : true;
  if (r.overrideMode === 'ARMED' && !expired) return `Đang bảo vệ ngoài lịch, tới ${until}, sau đó quay về lịch.`;
  if (r.overrideMode === 'DISARMED' && !expired) return `Đang tạm tắt, tới ${until}, sau đó quay về lịch.`;
  return 'Theo lịch. Thao tác tạm thời tự hết hiệu lực ở ranh giới khung giờ kế tiếp.';
}
