import { fmtNum } from './chartKit';

interface Props {
  label: string;
  value: number | null;
  unit: string;
  min: number;
  max: number;
  color: string;
  track: string;
  note?: string;
}

/**
 * Dong ho nua vong cho gia tri hien tai. Phan rong la buoc nhat hon cua cung
 * mot sac (meter, marks-and-anatomy), nen nhin ca vong van biet dang o dau thang.
 */
export function Gauge({ label, value, unit, min, max, color, track, note }: Props) {
  const r = 70;
  const cx = 86;
  const cy = 86;
  const frac = value == null ? 0 : Math.min(1, Math.max(0, (value - min) / (max - min)));
  const pt = (f: number) => {
    const a = Math.PI * (1 - f);
    return [cx + r * Math.cos(a), cy - r * Math.sin(a)];
  };
  const [sx, sy] = pt(0);
  const [ex, ey] = pt(1);
  const [vx, vy] = pt(frac);

  return (
    <figure className="gauge">
      <svg width={172} height={104} role="img" aria-label={`${label}: ${fmtNum(value)} ${unit}`}>
        <path d={`M${sx},${sy}A${r},${r} 0 0 1 ${ex},${ey}`} fill="none" stroke={track} strokeWidth={14} strokeLinecap="round" />
        {value != null && frac > 0 && (
          <path d={`M${sx},${sy}A${r},${r} 0 0 1 ${vx},${vy}`} fill="none" stroke={color} strokeWidth={14} strokeLinecap="round" />
        )}
        <text x={cx} y={cy - 8} textAnchor="middle" className="gauge-value">
          {fmtNum(value)}
        </text>
        <text x={cx} y={cy + 12} textAnchor="middle" className="axis-text">
          {unit}
        </text>
        <text x={sx} y={cy + 16} textAnchor="middle" className="axis-text">{min}</text>
        <text x={ex} y={cy + 16} textAnchor="middle" className="axis-text">{max}</text>
      </svg>
      <figcaption>
        <strong>{label}</strong>
        {note && <span className="chart-tip-muted">{note}</span>}
      </figcaption>
    </figure>
  );
}
