import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api';
import { duration } from '../../format';
import type { EnvSeries, Heatmap, OpensSeries, StateShare } from '../../types';
import { EnvLineChart } from './EnvLineChart';
import { HeatmapGrid } from './HeatmapGrid';
import { OpensColumns } from './OpensColumns';
import { StateDonuts } from './StateDonuts';
import { RH_COLOR, TEMP_COLOR, doorColor, fmtDay, fmtDayHour, fmtHour, fmtNum } from './chartKit';

const ENV_RANGES: Array<[number, string]> = [
  [24, '24 giờ'],
  [24 * 7, '7 ngày'],
  [24 * 30, '30 ngày'],
];
const OPEN_RANGES: Array<['hour' | 'day', number, string]> = [
  ['hour', 1, '24 giờ, theo giờ'],
  ['day', 7, '7 ngày'],
  ['day', 30, '30 ngày'],
];
const SHARE_RANGES: Array<[number, string]> = [
  [1, '24 giờ'],
  [7, '7 ngày'],
  [30, '30 ngày'],
];

const stepMinutes = (step: string) => {
  const [n, u] = step.split(' ');
  return Number(n) * (u.startsWith('hour') ? 60 : 1);
};

/**
 * Trang Thong ke (mon IoT va Ung dung): nam dang do thi tren hai loai du lieu,
 * su kien cua (MC-38) va so do lien tuc (AHT20). Moi so lieu da duoc gom nhom o
 * may chu (/api/stats/*), trinh duyet chi ve.
 */
export function StatsPage() {
  const [envHours, setEnvHours] = useState(24);
  const [openRange, setOpenRange] = useState(1);
  const [shareDays, setShareDays] = useState(7);

  const [env, setEnv] = useState<EnvSeries | null>(null);
  const [opens, setOpens] = useState<OpensSeries | null>(null);
  const [heat, setHeat] = useState<Heatmap | null>(null);
  const [share, setShare] = useState<StateShare[]>([]);
  const [error, setError] = useState<string | null>(null);

  const guard = useCallback(async <T,>(p: Promise<T>, set: (v: T) => void) => {
    try {
      set(await p);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Không tải được số liệu');
    }
  }, []);

  useEffect(() => {
    void guard(api.envSeries(envHours), setEnv);
  }, [envHours, guard]);
  useEffect(() => {
    const [unit, days] = OPEN_RANGES[openRange];
    void guard(api.opensSeries(unit, days), setOpens);
  }, [openRange, guard]);
  useEffect(() => {
    void guard(api.stateShare(shareDays), setShare);
  }, [shareDays, guard]);
  useEffect(() => {
    void guard(api.heatmap(28), setHeat);
  }, [guard]);

  const step = env ? stepMinutes(env.step) : 10;

  return (
    <>
      {error && (
        <p className="notice">
          <span className="notice-strong">Không tải được số liệu thống kê.</span> <code>{error}</code>
        </p>
      )}

      <section className="section">
        <div className="section-head">
          <div>
            <h2 className="section-title">Nhiệt độ và độ ẩm</h2>
            <p className="section-note">
              Đường là trung bình mỗi {env?.step.replace('minutes', 'phút').replace('hours', 'giờ') ?? '10 phút'}, dải mờ là thấp nhất
              đến cao nhất trong khoảng đó. Đường ngắt là lúc không nhận được số đo.
            </p>
          </div>
          <Segmented options={ENV_RANGES.map(([v, l]) => [v, l])} value={envHours} onChange={setEnvHours} label="Khoảng thời gian" />
        </div>
        <h3 className="chart-title">Nhiệt độ (°C)</h3>
        {env && <EnvLineChart points={env.points} field="temp" stepMinutes={step} hours={env.hours} color={TEMP_COLOR} unit="°C" label="Nhiệt độ" />}
        <h3 className="chart-title">Độ ẩm tương đối (%)</h3>
        {env && <EnvLineChart points={env.points} field="rh" stepMinutes={step} hours={env.hours} color={RH_COLOR} unit="%" label="Độ ẩm" />}
        {env && (
          <DataTable
            caption="Bảng số liệu nhiệt độ và độ ẩm"
            head={['Thời điểm', 'Nhiệt độ TB', 'Thấp nhất', 'Cao nhất', 'Độ ẩm TB', 'Thấp nhất', 'Cao nhất', 'Số mẫu']}
            rows={env.points.map((p) => [fmtDayHour(p.bucket), fmtNum(p.tempAvg), fmtNum(p.tempMin), fmtNum(p.tempMax),
              fmtNum(p.rhAvg), fmtNum(p.rhMin), fmtNum(p.rhMax), String(p.n)])}
          />
        )}
      </section>

      <section className="section">
        <div className="section-head">
          <div>
            <h2 className="section-title">Số lần mở cửa</h2>
            <p className="section-note">Tách từng cửa. Số ghi trên cột cao nhất của mỗi cửa.</p>
          </div>
          <Segmented options={OPEN_RANGES.map(([, , l], i) => [i, l])} value={openRange} onChange={setOpenRange} label="Khoảng thời gian" />
        </div>
        {opens && <Legend doors={opens.doors.map((d) => d.label)} />}
        {opens && <OpensColumns series={opens} />}
        {opens && (
          <DataTable
            caption="Bảng số lần mở"
            head={['Khoảng', ...opens.doors.map((d) => d.label)]}
            rows={opens.buckets.map((b, i) => [opens.unit === 'hour' ? `${fmtDay(b)} ${fmtHour(b)}` : fmtDay(b), ...opens.counts[i].map(String)])}
          />
        )}
      </section>

      <section className="section">
        <div className="section-head">
          <div>
            <h2 className="section-title">Tỉ lệ thời gian mở và đóng</h2>
            <p className="section-note">
              Tính từ khoảng cách giữa các sự kiện. Lúc thiết bị mất liên lạc vẫn tính theo trạng thái cuối cùng đã biết.
            </p>
          </div>
          <Segmented options={SHARE_RANGES.map(([v, l]) => [v, l])} value={shareDays} onChange={setShareDays} label="Khoảng thời gian" />
        </div>
        <StateDonuts data={share} />
        <DataTable
          caption="Bảng thời gian mở và đóng"
          head={['Cửa', 'Mở', 'Đóng', 'Tỉ lệ mở']}
          rows={share.map((s) => [s.label, duration(s.openSeconds), duration(s.closedSeconds),
            `${fmtNum((100 * s.openSeconds) / Math.max(1, s.openSeconds + s.closedSeconds), 2)}%`])}
        />
      </section>

      <section className="section">
        <h2 className="section-title">Nếp sinh hoạt: số lần mở theo thứ và giờ</h2>
        <p className="section-note">
          Trung bình mỗi ngày trong 28 ngày gần nhất, giờ Việt Nam.
        </p>
        {heat && <HeatmapGrid data={heat} />}
      </section>
    </>
  );
}

function Segmented<T extends string | number>({
  options,
  value,
  onChange,
  label,
}: {
  options: Array<[T, string]>;
  value: T;
  onChange: (v: T) => void;
  label: string;
}) {
  return (
    <div className="segmented" role="radiogroup" aria-label={label}>
      {options.map(([v, l]) => (
        <button key={String(v)} type="button" role="radio" aria-checked={v === value}
          className={`segmented-item${v === value ? ' is-on' : ''}`} onClick={() => onChange(v)}>
          {l}
        </button>
      ))}
    </div>
  );
}

function Legend({ doors }: { doors: string[] }) {
  return (
    <div className="legend">
      {doors.map((d, i) => (
        <span key={d} className="legend-item">
          <span className="swatch" style={{ background: doorColor(i) }} />
          {d}
        </span>
      ))}
    </div>
  );
}

/** Bang so lieu sau moi bieu do: nguoi khong phan biet mau, in den trang, hoac can so chinh xac. */
function DataTable({ caption, head, rows }: { caption: string; head: string[]; rows: string[][] }) {
  return (
    <details className="data-table">
      <summary>{caption}</summary>
      <div className="data-table-scroll">
        <table className="log">
          <thead>
            <tr>{head.map((h, i) => <th key={i} className={i ? 'log-num' : ''}>{h}</th>)}</tr>
          </thead>
          <tbody>
            {rows.map((r, i) => (
              <tr key={i}>{r.map((c, j) => <td key={j} className={j ? 'log-num' : ''}>{c}</td>)}</tr>
            ))}
          </tbody>
        </table>
      </div>
    </details>
  );
}
