import { useMemo, useState } from 'react';
import type { EnvPoint } from '../../types';
import { GRID, SURFACE, fmtDay, fmtDayHour, fmtHour, fmtNum, niceTicks, useWidth } from './chartKit';

interface Props {
  points: EnvPoint[];
  field: 'temp' | 'rh';
  /** Do dai mot khoang, phut. Khoang trong lon hon 2 lan buoc thi ngat duong. */
  stepMinutes: number;
  hours: number;
  color: string;
  unit: string;
  label: string;
}

const H = 220;
const PAD = { top: 16, right: 70, bottom: 28, left: 40 };

/**
 * Duong trung binh + dai min..max cua tung khoang.
 *
 * Dai min..max cho thay do dao dong trong khoang gom nhom: diem trung binh 10
 * phut giau mat mot lan bat dieu hoa lam nhiet do tut 2 do trong 3 phut.
 * Thiet bi mat lien lac thi ngat duong chu khong noi thang qua khoang trong,
 * vi noi thang la ve ra so lieu khong co.
 */
export function EnvLineChart({ points, field, stepMinutes, hours, color, unit, label }: Props) {
  const [box, width] = useWidth<HTMLDivElement>();
  const [hover, setHover] = useState<number | null>(null);

  const data = useMemo(
    () =>
      points
        .map((p) => ({
          t: new Date(p.bucket).getTime(),
          iso: p.bucket,
          avg: field === 'temp' ? p.tempAvg : p.rhAvg,
          min: field === 'temp' ? p.tempMin : p.rhMin,
          max: field === 'temp' ? p.tempMax : p.rhMax,
          n: p.n,
        }))
        .filter((d) => d.avg != null) as Array<{ t: number; iso: string; avg: number; min: number; max: number; n: number }>,
    [points, field],
  );

  if (data.length === 0) {
    return <p className="log-empty">Chưa có số đo {label.toLowerCase()} trong khoảng này.</p>;
  }

  const innerW = width - PAD.left - PAD.right;
  const innerH = H - PAD.top - PAD.bottom;
  const t1 = Date.now();
  const t0 = t1 - hours * 3600e3;
  const lo = Math.min(...data.map((d) => d.min));
  const hi = Math.max(...data.map((d) => d.max));
  const ticks = niceTicks(lo, hi, 4);
  const yMin = ticks[0];
  const yMax = ticks[ticks.length - 1];
  const x = (t: number) => PAD.left + ((t - t0) / (t1 - t0)) * innerW;
  const y = (v: number) => PAD.top + (1 - (v - yMin) / (yMax - yMin)) * innerH;

  // Tach thanh cac doan lien tuc.
  const gap = stepMinutes * 60e3 * 2.01;
  const runs: (typeof data)[] = [];
  data.forEach((d, i) => {
    if (i === 0 || d.t - data[i - 1].t > gap) runs.push([]);
    runs[runs.length - 1].push(d);
  });

  const line = (r: typeof data) => r.map((d, i) => `${i ? 'L' : 'M'}${x(d.t).toFixed(1)},${y(d.avg).toFixed(1)}`).join('');
  const band = (r: typeof data) =>
    r.map((d, i) => `${i ? 'L' : 'M'}${x(d.t).toFixed(1)},${y(d.max).toFixed(1)}`).join('') +
    [...r].reverse().map((d) => `L${x(d.t).toFixed(1)},${y(d.min).toFixed(1)}`).join('') +
    'Z';

  // Moc truc x: theo gio cho 1 ngay, theo ngay cho khoang dai.
  const xTicks: number[] = [];
  const stepH = hours <= 24 ? 4 : hours <= 72 ? 12 : hours <= 24 * 7 ? 24 : 24 * 5;
  const first = new Date(t0);
  first.setMinutes(0, 0, 0);
  if (stepH >= 24) first.setHours(0);
  for (let t = first.getTime(); t <= t1; t += stepH * 3600e3) {
    if (t >= t0) xTicks.push(t);
  }
  const xLabel = (t: number) => (stepH >= 24 ? fmtDay(new Date(t).toISOString()) : fmtHour(new Date(t).toISOString()));

  const last = data[data.length - 1];
  const h = hover != null ? data[hover] : null;

  const onMove = (e: React.PointerEvent<SVGRectElement>) => {
    const rect = (e.currentTarget as SVGRectElement).getBoundingClientRect();
    const t = t0 + ((e.clientX - rect.left) / rect.width) * (t1 - t0);
    let best = 0;
    for (let i = 1; i < data.length; i++) if (Math.abs(data[i].t - t) < Math.abs(data[best].t - t)) best = i;
    setHover(best);
  };

  return (
    <div className="chart-box" ref={box}>
      <svg width={width} height={H} role="img" aria-label={`${label} theo thời gian, ${hours} giờ gần nhất`}>
        {ticks.map((v) => (
          <g key={v}>
            <line x1={PAD.left} x2={width - PAD.right} y1={y(v)} y2={y(v)} stroke={GRID} strokeWidth={1} />
            <text x={PAD.left - 6} y={y(v) + 4} textAnchor="end" className="axis-text">
              {fmtNum(v, Number.isInteger(v) ? 0 : 1)}
            </text>
          </g>
        ))}
        {xTicks.map((t) => (
          <text key={t} x={x(t)} y={H - 8} textAnchor="middle" className="axis-text">
            {xLabel(t)}
          </text>
        ))}

        {runs.map((r, i) => (
          <path key={`b${i}`} d={band(r)} fill={color} opacity={0.12} />
        ))}
        {runs.map((r, i) =>
          r.length === 1 ? (
            <circle key={`l${i}`} cx={x(r[0].t)} cy={y(r[0].avg)} r={2} fill={color} />
          ) : (
            <path key={`l${i}`} d={line(r)} fill="none" stroke={color} strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" />
          ),
        )}

        {/* Nhan o dau mut: gia tri gan nhat. Chu mang mau chu, khong mang mau du lieu. */}
        <circle cx={x(last.t)} cy={y(last.avg)} r={4} fill={color} stroke={SURFACE} strokeWidth={2} />
        <text x={x(last.t) + 8} y={y(last.avg) + 4} className="end-label">
          {fmtNum(last.avg)} {unit}
        </text>

        {h && (
          <g pointerEvents="none">
            <line x1={x(h.t)} x2={x(h.t)} y1={PAD.top} y2={H - PAD.bottom} stroke="var(--graphite-soft)" strokeWidth={1} />
            <circle cx={x(h.t)} cy={y(h.avg)} r={4} fill={color} stroke={SURFACE} strokeWidth={2} />
          </g>
        )}
        <rect
          x={PAD.left}
          y={PAD.top}
          width={innerW}
          height={innerH}
          fill="transparent"
          onPointerMove={onMove}
          onPointerLeave={() => setHover(null)}
        />
      </svg>
      {h && (
        <div
          className="chart-tip"
          style={{ left: Math.min(Math.max(x(h.t), 70), width - 70), top: PAD.top }}
          role="status"
        >
          <div className="chart-tip-head">{fmtDayHour(h.iso)}</div>
          <div>
            Trung bình <strong>{fmtNum(h.avg)} {unit}</strong>
          </div>
          <div className="chart-tip-muted">
            Thấp nhất {fmtNum(h.min)} · cao nhất {fmtNum(h.max)} · {h.n} mẫu
          </div>
        </div>
      )}
    </div>
  );
}
