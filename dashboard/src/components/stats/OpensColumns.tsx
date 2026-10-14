import { useState } from 'react';
import type { OpensSeries } from '../../types';
import { GRID, doorColor, fmtDay, fmtHour, niceTicks, useWidth } from './chartKit';

const H = 220;
const PAD = { top: 18, right: 12, bottom: 28, left: 34 };

/** Cot bo goc 4px o dau du lieu, vuong o day (marks-and-anatomy). */
function columnPath(x: number, y: number, w: number, h: number) {
  if (h <= 0) return '';
  const r = Math.min(4, w / 2, h);
  return `M${x},${y + h}V${y + r}Q${x},${y} ${x + r},${y}H${x + w - r}Q${x + w},${y} ${x + w},${y + r}V${y + h}Z`;
}

/**
 * So lan mo theo gio hoac ngay, cot cua tung cua dung canh nhau trong moi khoang.
 * Chon cot canh nhau thay vi cot chong: so lan mo cua tung cua moi la cau hoi,
 * tong ca nha thi it y nghia.
 */
export function OpensColumns({ series }: { series: OpensSeries }) {
  const [box, width] = useWidth<HTMLDivElement>();
  const [hover, setHover] = useState<number | null>(null);
  const { doors, buckets, counts } = series;

  if (buckets.length === 0 || doors.length === 0) {
    return <p className="log-empty">Chưa có lần mở cửa nào trong khoảng này.</p>;
  }

  const innerW = width - PAD.left - PAD.right;
  const innerH = H - PAD.top - PAD.bottom;
  const maxV = Math.max(1, ...counts.flat());
  const ticks = niceTicks(0, maxV, 4).filter((v) => Number.isInteger(v));
  const yMax = ticks[ticks.length - 1];
  const slot = innerW / buckets.length;
  const gapPx = 2; // khe 2px giua hai cot ke nhau
  const barW = Math.max(2, Math.min(24, (slot * 0.7 - gapPx * (doors.length - 1)) / doors.length));
  const groupW = barW * doors.length + gapPx * (doors.length - 1);
  const y = (v: number) => PAD.top + (1 - v / yMax) * innerH;

  // Nhan chon loc: chi ghi so tren cot cao nhat cua moi cua.
  const peak = doors.map((_, j) => {
    let best = 0;
    counts.forEach((row, i) => {
      if (row[j] > counts[best][j]) best = i;
    });
    return counts[best][j] > 0 ? best : -1;
  });

  const label = (iso: string) => (series.unit === 'hour' ? fmtHour(iso) : fmtDay(iso));
  const every = Math.ceil(buckets.length / Math.max(2, Math.floor(innerW / 56)));

  return (
    <div className="chart-box" ref={box}>
      <svg width={width} height={H} role="img" aria-label="Số lần mở cửa theo thời gian, tách từng cửa">
        {ticks.map((v) => (
          <g key={v}>
            <line x1={PAD.left} x2={width - PAD.right} y1={y(v)} y2={y(v)} stroke={GRID} strokeWidth={1} />
            <text x={PAD.left - 6} y={y(v) + 4} textAnchor="end" className="axis-text">
              {v}
            </text>
          </g>
        ))}
        {buckets.map((b, i) => {
          const gx = PAD.left + slot * i + (slot - groupW) / 2;
          return (
            <g key={b}>
              {doors.map((d, j) => {
                const v = counts[i][j];
                const bx = gx + j * (barW + gapPx);
                return (
                  <g key={d.id}>
                    <path d={columnPath(bx, y(v), barW, innerH + PAD.top - y(v))} fill={doorColor(j)} />
                    {peak[j] === i && (
                      <text x={bx + barW / 2} y={y(v) - 4} textAnchor="middle" className="axis-text">
                        {v}
                      </text>
                    )}
                  </g>
                );
              })}
              {i % every === 0 && (
                <text x={PAD.left + slot * i + slot / 2} y={H - 8} textAnchor="middle" className="axis-text">
                  {label(b)}
                </text>
              )}
              {/* Vung re chuot rong ca khoang, lon hon cot. */}
              <rect
                x={PAD.left + slot * i}
                y={PAD.top}
                width={slot}
                height={innerH}
                fill={hover === i ? 'rgba(30,42,40,0.05)' : 'transparent'}
                onPointerEnter={() => setHover(i)}
                onPointerLeave={() => setHover(null)}
              />
            </g>
          );
        })}
        <line x1={PAD.left} x2={width - PAD.right} y1={PAD.top + innerH} y2={PAD.top + innerH} stroke="var(--graphite)" strokeWidth={1} />
      </svg>
      {hover != null && (
        <div
          className="chart-tip"
          style={{ left: Math.min(Math.max(PAD.left + slot * hover + slot / 2, 70), width - 70), top: PAD.top }}
          role="status"
        >
          <div className="chart-tip-head">
            {series.unit === 'hour' ? `${fmtDay(buckets[hover])} ${label(buckets[hover])}` : fmtDay(buckets[hover])}
          </div>
          {doors.map((d, j) => (
            <div key={d.id} className="chart-tip-row">
              <span className="swatch" style={{ background: doorColor(j) }} />
              {d.label}: <strong>{counts[hover][j]}</strong> lần
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
