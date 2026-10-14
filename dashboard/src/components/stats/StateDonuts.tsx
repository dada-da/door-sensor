import { useState } from 'react';
import type { StateShare } from '../../types';
import { duration } from '../../format';
import { CLOSED_COLOR, OPEN_COLOR, SURFACE, fmtNum } from './chartKit';

const R = 62;
const W = 18;

/** Cung tron tu goc a0 toi a1 (radian, 0 = 12 gio, theo chieu kim dong ho). */
function arc(a0: number, a1: number, r: number) {
  const p = (a: number) => [R + 8 + r * Math.sin(a), R + 8 - r * Math.cos(a)];
  const [x0, y0] = p(a0);
  const [x1, y1] = p(a1);
  const large = a1 - a0 > Math.PI ? 1 : 0;
  return `M${x0},${y0}A${r},${r} 0 ${large} 1 ${x1},${y1}`;
}

/**
 * Ti le thoi gian mo so voi dong, moi cua mot vong.
 *
 * Phan "mo" thuong rat nho (vai phan tram), nen con so lon o giua vong mang
 * thong tin chinh, vong chi de nhin ti le. Duoi 1 do cung thi van ve mot vach
 * toi thieu de khong bien mat, so that van o giua va trong bang.
 */
export function StateDonuts({ data }: { data: StateShare[] }) {
  const [hover, setHover] = useState<string | null>(null);
  if (data.length === 0) return <p className="log-empty">Chưa có dữ liệu đóng mở.</p>;

  return (
    <div className="donuts">
      {data.map((d) => {
        const total = d.openSeconds + d.closedSeconds;
        const share = total > 0 ? d.openSeconds / total : 0;
        // Khe 2px mau nen giua hai phan (surface gap).
        const gapA = 2 / R;
        const openA = share > 0 ? Math.max(share * 2 * Math.PI, 0.03) : 0;
        const r = R - W / 2;
        return (
          <figure key={d.deviceId} className="donut" onPointerEnter={() => setHover(d.deviceId)} onPointerLeave={() => setHover(null)}>
            <svg width={2 * R + 16} height={2 * R + 16} role="img"
              aria-label={`${d.label}: mở ${fmtNum(share * 100, 1)} phần trăm thời gian`}>
              <path d={arc(openA + gapA, 2 * Math.PI - gapA, r)} fill="none" stroke={CLOSED_COLOR} strokeWidth={W} />
              {openA > 0 && <path d={arc(0, openA, r)} fill="none" stroke={OPEN_COLOR} strokeWidth={W} />}
              <circle cx={R + 8} cy={R + 8} r={R - W - 1} fill={SURFACE} />
              <text x={R + 8} y={R + 6} textAnchor="middle" className="donut-value">
                {fmtNum(share * 100, share < 0.1 ? 1 : 0)}%
              </text>
              <text x={R + 8} y={R + 24} textAnchor="middle" className="axis-text">
                thời gian mở
              </text>
            </svg>
            <figcaption>
              <strong>{d.label}</strong>
              <span className="chart-tip-muted">
                {hover === d.deviceId
                  ? `Mở ${duration(d.openSeconds)}, đóng ${duration(d.closedSeconds)}`
                  : `Mở ${duration(d.openSeconds)}`}
              </span>
            </figcaption>
          </figure>
        );
      })}
      <div className="legend">
        <span className="legend-item"><span className="swatch" style={{ background: OPEN_COLOR }} />Mở</span>
        <span className="legend-item"><span className="swatch" style={{ background: CLOSED_COLOR }} />Đóng</span>
      </div>
    </div>
  );
}
