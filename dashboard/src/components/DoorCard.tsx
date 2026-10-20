import { useEffect, useState } from 'react';
import { api } from '../api';
import { clockTime, duration } from '../format';
import type { DeviceStatus, DoorRule } from '../types';
import { Sparkline } from './Sparkline';

interface Props {
  device: DeviceStatus;
  /** Luat bao dong cua cua nay (useAlarm), de hien nhan "dang bao ve" ngay tren the. */
  rule?: DoorRule;
}

/** Ngay sau khi sac day may chu moi biet; xu huong pin lay 48 gio, cap nhat moi 5 phut. */
const TREND_HOURS = 48;
const TREND_REFRESH_MS = 5 * 60 * 1000;

/**
 * The trang thai mot cua: thay mat bang ve canh cua cu. Mot the gom tat ca thong
 * tin can nhin nhanh (trang thai, pin, song, tin gan nhat), xep luoi nhieu the
 * tren mot hang.
 */
export function DoorCard({ device, rule }: Props) {
  const isOpen = device.state === 'OPEN';
  const offline = !device.online;
  const stateCls = offline ? 'is-offline' : isOpen ? 'is-open' : 'is-closed';

  return (
    <article className={`card door-card ${stateCls}`} aria-label={device.label}>
      <header className="card-head">
        <div>
          <h2 className="card-title">{device.label}</h2>
          <span className="card-sub">
            {device.id}
            {device.location && device.location !== device.label ? ` · ${device.location}` : ''}
          </span>
        </div>
        {rule && (
          <span className={`door-rule-badge${rule.armedNow ? ' is-armed' : ''}`} title={modeTitle(rule)}>
            {rule.mode === 'OFF' ? 'Tắt báo động' : rule.armedNow ? 'Đang bảo vệ' : 'Không bảo vệ'}
          </span>
        )}
      </header>

      <div className="door-state-line">
        <span className={`door-state ${stateCls}`}>{stateText(device)}</span>
        {device.stateForSeconds != null && device.online && (
          <span className="door-elapsed">{duration(device.stateForSeconds)}</span>
        )}
      </div>

      <BatteryBlock device={device} />

      <dl className="facts">
        <div>
          <dt>Sóng</dt>
          <dd>
            <SignalBars rssi={device.rssi} />
            {device.rssi != null ? `${device.rssi} dBm` : '--'}
          </dd>
        </div>
        <div className={offline ? 'is-alert' : undefined}>
          <dt>Tin gần nhất</dt>
          <dd>{clockTime(device.lastSeen)}</dd>
        </div>
        <div>
          <dt>Đổi trạng thái</dt>
          <dd>{clockTime(device.stateSince)}</dd>
        </div>
        <div>
          <dt>Khởi động</dt>
          <dd>{device.bootCount != null ? `#${device.bootCount}` : '--'}</dd>
        </div>
      </dl>
    </article>
  );
}

function stateText(d: DeviceStatus): string {
  if (!d.online) return 'Mất liên lạc';
  if (d.state === 'OPEN') return 'Đang mở';
  if (d.state === 'CLOSED') return 'Đang đóng';
  return 'Chưa rõ';
}

function modeTitle(r: DoorRule): string {
  if (r.mode === 'ALWAYS') return 'Luôn bật báo động';
  if (r.mode === 'OFF') return 'Đã tắt báo động cho cửa này';
  return r.inWindowNow ? 'Theo lịch, đang trong khung giờ' : 'Theo lịch, ngoài khung giờ';
}

// ---------------------------------------------------------------- pin

type Level = 'good' | 'mid' | 'low' | 'critical';

/** Nguong mau theo phan tram (backend da quy doi bang duong cong Li-ion). */
function level(pct: number): Level {
  if (pct >= 40) return 'good';
  if (pct >= 20) return 'mid';
  if (pct >= 10) return 'low';
  return 'critical';
}

const LEVEL_TEXT: Record<Level, string> = {
  good: 'Tốt',
  mid: 'Trung bình',
  low: 'Yếu, nên sạc',
  critical: 'Sắp hết',
};

function BatteryBlock({ device }: { device: DeviceStatus }) {
  const mv = device.batteryMv;
  const pct = device.batteryPercent;

  if (mv == null || pct == null) {
    return (
      <div className="batt is-usb">
        <span className="batt-usb">Nguồn USB</span>
        <span className="batt-note">Thiết bị không đo pin</span>
      </div>
    );
  }

  const lv = level(pct);
  return (
    <div className={`batt is-${lv}`}>
      <div className="batt-row">
        <BatteryIcon pct={pct} />
        <span className="batt-pct">
          {pct}
          <small>%</small>
        </span>
        <span className="batt-meta">
          <strong>{LEVEL_TEXT[lv]}</strong>
          <span>{(mv / 1000).toFixed(2).replace('.', ',')} V</span>
        </span>
      </div>
      <BatteryTrend deviceId={device.id} />
    </div>
  );
}

/** Hinh pin nam ngang, ruot to theo phan tram va doi mau theo muc. */
function BatteryIcon({ pct }: { pct: number }) {
  const w = 64;
  const h = 30;
  const inner = w - 6;
  const fill = Math.max(pct > 0 ? 3 : 0, (inner * pct) / 100);
  return (
    <svg className="batt-icon" width={w + 6} height={h} viewBox={`0 0 ${w + 6} ${h}`} aria-hidden="true">
      <rect x={1} y={1} width={w} height={h - 2} rx={4} className="batt-shell" />
      <rect x={w + 1} y={h / 2 - 6} width={4} height={12} rx={1.5} className="batt-cap" />
      <rect x={4} y={4} width={fill} height={h - 8} rx={2} className="batt-fill" />
      {[0.25, 0.5, 0.75].map((f) => (
        <line key={f} x1={4 + inner * f} y1={4} x2={4 + inner * f} y2={h - 4} className="batt-tick" />
      ))}
    </svg>
  );
}

/**
 * Xu huong dien ap 48 gio. Lay tu device_telemetry (moi lan thuc), nen thay ca
 * chieu sut ap luc thu nghiem tang toc lan luc dung that.
 */
function BatteryTrend({ deviceId }: { deviceId: string }) {
  const [values, setValues] = useState<Array<number | null>>([]);

  useEffect(() => {
    let alive = true;
    const load = () =>
      api
        .deviceSeries(deviceId, TREND_HOURS)
        .then((s) => alive && setValues(s.points.map((p) => p.battAvg)))
        .catch(() => undefined);
    void load();
    const t = window.setInterval(load, TREND_REFRESH_MS);
    return () => {
      alive = false;
      window.clearInterval(t);
    };
  }, [deviceId]);

  const known = values.filter((v): v is number => v != null);
  const delta = known.length >= 2 ? Math.round(known[known.length - 1] - known[0]) : null;

  return (
    <div className="batt-trend">
      <Sparkline values={values} color="currentColor" label={`Điện áp pin ${TREND_HOURS} giờ qua`} />
      <span className="batt-trend-text">
        {delta == null ? `${TREND_HOURS} giờ qua` : `${delta > 0 ? '+' : ''}${delta} mV / ${TREND_HOURS} giờ`}
      </span>
    </div>
  );
}

// ---------------------------------------------------------------- song

/** Bon vach kieu dien thoai: -55 dBm tro len du 4 vach, duoi -85 con 1. */
function SignalBars({ rssi }: { rssi: number | null }) {
  const bars = rssi == null ? 0 : rssi >= -55 ? 4 : rssi >= -67 ? 3 : rssi >= -77 ? 2 : 1;
  return (
    <svg className={`signal${bars <= 1 ? ' is-weak' : ''}`} width={18} height={14} viewBox="0 0 18 14" aria-hidden="true">
      {[0, 1, 2, 3].map((i) => (
        <rect
          key={i}
          x={i * 4.5}
          y={14 - (i + 1) * 3.5}
          width={3}
          height={(i + 1) * 3.5}
          rx={0.6}
          className={i < bars ? 'signal-on' : 'signal-off'}
        />
      ))}
    </svg>
  );
}
