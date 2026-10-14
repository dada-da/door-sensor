import { useState } from 'react';
import type { Heatmap } from '../../types';
import { SEQ, fmtNum } from './chartKit';

const DOW = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];
const DOW_FULL = ['Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy', 'Chủ nhật'];

/**
 * So lan mo trung binh moi ngay theo thu x gio.
 *
 * Chia cho so ngay cua tung thu (daysPerDow): 30 ngay thi co thu xuat hien 5
 * lan, co thu 4 lan; dem tho se lam thu do dam hon ma khong phai do sinh hoat.
 * Thang mau mot sac, o 0 de mau nen de "khong co gi" lui ve sau.
 */
export function HeatmapGrid({ data }: { data: Heatmap }) {
  const [hover, setHover] = useState<{ d: number; h: number } | null>(null);
  const avg = data.cells.map((row, d) => row.map((v) => (data.daysPerDow[d] ? v / data.daysPerDow[d] : 0)));
  const maxAvg = Math.max(0, ...avg.flat());

  const color = (v: number) => {
    // O 0: mot buoc lech khoi nen, du thay luoi ma van lui ve sau.
    if (v <= 0 || maxAvg <= 0) return '#e1e7e5';
    const i = Math.min(SEQ.length - 1, Math.floor((v / maxAvg) * SEQ.length));
    return SEQ[i];
  };

  return (
    <div className="heatmap-wrap">
      <div className="heatmap" role="grid" aria-label="Số lần mở trung bình mỗi ngày theo thứ và giờ">
        <div className="heatmap-corner" />
        {Array.from({ length: 24 }, (_, h) => (
          <div key={h} className="heatmap-hour">
            {h % 3 === 0 ? String(h).padStart(2, '0') : ''}
          </div>
        ))}
        {avg.map((row, d) => (
          <div key={d} className="heatmap-row" role="row">
            <div className="heatmap-dow">{DOW[d]}</div>
            {row.map((v, h) => (
              <div
                key={h}
                role="gridcell"
                aria-label={`${DOW_FULL[d]} ${h} giờ: ${fmtNum(v, 1)} lần mỗi ngày`}
                className={`heatmap-cell${hover?.d === d && hover?.h === h ? ' is-hover' : ''}`}
                style={{ background: color(v) }}
                onPointerEnter={() => setHover({ d, h })}
                onPointerLeave={() => setHover(null)}
              />
            ))}
          </div>
        ))}
      </div>
      <div className="heatmap-foot">
        <span className="chart-tip-muted" role="status">
          {hover
            ? `${DOW_FULL[hover.d]}, ${String(hover.h).padStart(2, '0')}:00 đến ${String(hover.h + 1).padStart(2, '0')}:00: ` +
              `${fmtNum(avg[hover.d][hover.h], 1)} lần mỗi ngày (${data.cells[hover.d][hover.h]} lần trong ${data.daysPerDow[hover.d]} ngày)`
            : `${data.days} ngày gần nhất, mọi cửa. Rê chuột lên một ô để xem số.`}
        </span>
        <span className="heatmap-scale" aria-hidden="true">
          ít
          {SEQ.map((c) => (
            <span key={c} style={{ background: c }} />
          ))}
          nhiều ({fmtNum(maxAvg, 1)} lần/ngày)
        </span>
      </div>
    </div>
  );
}
