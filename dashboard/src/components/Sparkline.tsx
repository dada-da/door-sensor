interface Props {
  values: Array<number | null>;
  color: string;
  height?: number;
  /** Dai nen to nhat (vd. khoang an toan giua hai nguong). */
  band?: [number | null, number | null];
  /** Ep truc doc bao gom cac gia tri nay (de dai nguong khong bi cat). */
  domain?: [number, number];
  label: string;
}

/**
 * Duong xu huong nho, khong truc. Chi de nhin chieu di len hay xuong; so cu the
 * nam o chu ben canh va o trang Thong ke.
 *
 * ViewBox co dinh 100 x height, gian theo be ngang khung; non-scaling-stroke giu
 * net 1,5 px du khung rong hay hep.
 */
export function Sparkline({ values, color, height = 34, band, domain, label }: Props) {
  const pts = values
    .map((v, i) => (v == null ? null : ([i, v] as const)))
    .filter((p): p is readonly [number, number] => p !== null);
  if (pts.length < 2) {
    return <div className="spark-empty">Chưa đủ dữ liệu để vẽ xu hướng</div>;
  }

  let lo = Math.min(...pts.map((p) => p[1]));
  let hi = Math.max(...pts.map((p) => p[1]));
  if (domain) {
    lo = Math.min(lo, domain[0]);
    hi = Math.max(hi, domain[1]);
  }
  if (hi - lo < 1e-9) {
    lo -= 1;
    hi += 1;
  }
  const pad = 3;
  const n = values.length - 1;
  const x = (i: number) => (i / n) * 100;
  const y = (v: number) => pad + (1 - (v - lo) / (hi - lo)) * (height - 2 * pad);
  const d = pts.map(([i, v], k) => `${k ? 'L' : 'M'}${x(i).toFixed(2)},${y(v).toFixed(2)}`).join('');

  let bandRect = null;
  if (band && (band[0] != null || band[1] != null)) {
    const top = band[1] != null ? Math.max(0, y(band[1])) : 0;
    const bottom = band[0] != null ? Math.min(height, y(band[0])) : height;
    if (bottom > top) {
      bandRect = <rect x={0} y={top} width={100} height={bottom - top} className="spark-band" />;
    }
  }

  return (
    <svg className="spark" viewBox={`0 0 100 ${height}`} preserveAspectRatio="none" height={height} role="img" aria-label={label}>
      {bandRect}
      <path d={d} fill="none" stroke={color} strokeWidth={1.5} vectorEffect="non-scaling-stroke" strokeLinejoin="round" />
    </svg>
  );
}
