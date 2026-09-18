import type { DoorState } from '../types';

interface Props {
  state: DoorState | null;
  online: boolean;
}

export function DoorPlan({ state, online }: Props) {
  const isOpen = state === 'OPEN';
  const angle = isOpen ? -90 : 0;

  const unknown = state === null || !online;
  const leafColor = unknown
    ? 'var(--construction)'
    : isOpen
      ? 'var(--annotate)'
      : 'var(--graphite)';

  return (
    <figure className="plan-figure">
      <svg
        viewBox="0 52 340 216"
        role="img"
        aria-label={ariaLabel(state, online)}
      >
        <g stroke="var(--graphite)" strokeWidth="9" strokeLinecap="butt">
          <line x1="8" y1="210" x2="90" y2="210" />
          <line x1="230" y1="210" x2="332" y2="210" />
        </g>

        <g stroke="var(--construction)" strokeWidth="1">
          {[16, 28, 40, 52, 64, 76, 240, 252, 264, 276, 288, 300, 312, 324].map(
            (x) => (
              <line key={x} x1={x} y1="206" x2={x + 8} y2="214" />
            ),
          )}
        </g>

        <path
          d="M 230 210 A 140 140 0 0 0 90 70"
          fill="none"
          stroke="var(--construction)"
          strokeWidth="1.5"
          strokeDasharray="5 5"
        />

        <rect
          x="232"
          y="192"
          width="26"
          height="11"
          rx="1.5"
          fill="var(--paper-lift)"
          stroke="var(--graphite)"
          strokeWidth="1.5"
        />
        <line
          x1="245"
          y1="192"
          x2="245"
          y2="164"
          stroke="var(--graphite-soft)"
          strokeWidth="1"
        />
        <text
          x="249"
          y="160"
          fontFamily="var(--font-label)"
          fontSize="14"
          fill="var(--graphite-soft)"
        >
          cảm biến
        </text>

        <g
          className="door-leaf"
          style={{
            transform: `rotate(${angle}deg)`,
            transformOrigin: '90px 210px',
            transformBox: 'view-box',
          }}
        >
          <line
            x1="90"
            y1="210"
            x2="230"
            y2="210"
            stroke={leafColor}
            strokeWidth="7"
            strokeLinecap="round"
          />
          <rect
            x="202"
            y="192"
            width="24"
            height="11"
            rx="1.5"
            fill={leafColor}
          />
        </g>

        <circle
          cx="90"
          cy="210"
          r="5"
          fill="var(--paper)"
          stroke="var(--graphite)"
          strokeWidth="2.5"
        />

        {/* Đường kích thước ô cửa */}
        <g stroke="var(--graphite-soft)" strokeWidth="1">
          <line x1="90" y1="246" x2="230" y2="246" />
          <line x1="90" y1="240" x2="90" y2="252" />
          <line x1="230" y1="240" x2="230" y2="252" />
        </g>
        <text
          x="160"
          y="264"
          textAnchor="middle"
          fontFamily="var(--font-label)"
          fontSize="14"
          fill="var(--graphite-soft)"
        >
          ô cửa
        </text>
      </svg>
      <figcaption className="plan-caption">
        Mặt bằng cửa. Cánh vẽ theo vị trí cảm biến đang báo.
      </figcaption>
    </figure>
  );
}

function ariaLabel(state: DoorState | null, online: boolean): string {
  if (!online) return 'Mặt bằng cửa, thiết bị đang mất liên lạc';
  if (state === 'OPEN') return 'Mặt bằng cửa, cửa đang mở';
  if (state === 'CLOSED') return 'Mặt bằng cửa, cửa đang đóng';
  return 'Mặt bằng cửa, chưa có dữ liệu';
}
