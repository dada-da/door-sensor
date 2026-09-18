/** Cac ham dinh dang dung chung. Tach ra de khong lap logic giua cac component. */

const TIME = new Intl.DateTimeFormat('vi-VN', {
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
});

const DATE_TIME = new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit',
  month: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});

export function clockTime(iso: string | null): string {
  if (!iso) return '--:--:--';
  return TIME.format(new Date(iso));
}

export function dateTime(iso: string | null): string {
  if (!iso) return '--';
  return DATE_TIME.format(new Date(iso));
}

export function hourLabel(iso: string): string {
  return String(new Date(iso).getHours()).padStart(2, '0');
}

export function duration(seconds: number | null | undefined): string {
  if (seconds === null || seconds === undefined) return '--';
  const s = Math.max(0, Math.floor(seconds));

  if (s < 60) return `${s} giây`;

  const m = Math.floor(s / 60);
  if (m < 60) {
    const rest = s % 60;
    return rest ? `${m} phút ${rest} giây` : `${m} phút`;
  }

  const h = Math.floor(m / 60);
  if (h < 24) {
    const rest = m % 60;
    return rest ? `${h} giờ ${rest} phút` : `${h} giờ`;
  }

  const d = Math.floor(h / 24);
  const rest = h % 24;
  return rest ? `${d} ngày ${rest} giờ` : `${d} ngày`;
}

export function durationMs(ms: number | null | undefined): string {
  if (ms === null || ms === undefined) return '--';
  return duration(Math.round(ms / 1000));
}

export function volts(mv: number | null | undefined): string {
  if (mv === null || mv === undefined) return '--';
  return `${(mv / 1000).toFixed(2)} V`;
}
