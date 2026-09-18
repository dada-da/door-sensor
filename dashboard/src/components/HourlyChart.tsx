import type { HourlyCount } from '../types';
import { hourLabel } from '../format';

interface Props {
  data: HourlyCount[];
}

export function HourlyChart({ data }: Props) {
  const max = Math.max(1, ...data.map((d) => d.opens));
  const total = data.reduce((sum, d) => sum + d.opens, 0);

  return (
    <div>
      <div className="chart">
        {data.map((d) => {
          const height = d.opens === 0 ? 2 : Math.round((d.opens / max) * 100);
          return (
            <div
              key={d.hour}
              className={`chart-col${d.opens === 0 ? ' is-empty' : ''}`}
              title={`${hourLabel(d.hour)} giờ: ${d.opens} lần`}
            >
              {d.opens > 0 && <span className="chart-count">{d.opens}</span>}
              <span
                className="chart-bar"
                style={{ height: d.opens === 0 ? '2px' : `${height}%` }}
              />
            </div>
          );
        })}
      </div>
      <div className="chart-axis">
        {data.map((d, i) => (
          // Chi ghi nhan moi 3 gio, neu khong cac so se dinh vao nhau tren
          // man hinh hep.
          <span key={d.hour} className="chart-tick">
            {i % 3 === 0 ? hourLabel(d.hour) : ''}
          </span>
        ))}
      </div>
      <p className="section-note" style={{ marginTop: 14, marginBottom: 0 }}>
        Tổng {total} lần mở trong 24 giờ qua. Cột cao nhất {max} lần.
      </p>
    </div>
  );
}
