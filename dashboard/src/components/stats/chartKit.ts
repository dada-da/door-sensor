import { useEffect, useRef, useState } from 'react';

/**
 * Mau theo vai tro (da chay validate_palette.js tren nen #e9edeb, 14/10):
 * - DOOR: nhan dien tung cua, thu tu co dinh theo danh sach cua, khong xoay vong.
 *   Cam 2,71:1 duoi 3:1 nen moi bieu do co nhan hien san va bang so lieu.
 * - OPEN / CLOSED: giu nghia san co cua giao dien (do ghi chu = mo).
 * - SEQ: mot sac xanh tu nhat toi dam cho heatmap.
 */
export const DOOR_COLORS = ['#2a78d6', '#eb6834'];
export const OTHER_COLOR = '#8c9a97';
export const OPEN_COLOR = '#a62e14';
export const CLOSED_COLOR = '#2a78d6';
export const TEMP_COLOR = '#eb6834';
export const RH_COLOR = '#2a78d6';
export const SEQ = ['#cde2fb', '#9ec5f4', '#6da7ec', '#3987e5', '#256abf', '#184f95', '#0d366b'];
export const GRID = '#c6cfcd';
export const SURFACE = '#e9edeb';

export const doorColor = (i: number) => DOOR_COLORS[i] ?? OTHER_COLOR;

/** Do chieu rong that cua khung chua de ve SVG dung pixel, khong keo gian chu. */
export function useWidth<T extends HTMLElement>(fallback = 640) {
  const ref = useRef<T | null>(null);
  const [width, setWidth] = useState(fallback);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const ro = new ResizeObserver(([e]) => setWidth(Math.max(260, Math.floor(e.contentRect.width))));
    ro.observe(el);
    return () => ro.disconnect();
  }, []);
  return [ref, width] as const;
}

/** Moc truc dep: 1, 2, 2.5, 5 x 10^k. */
export function niceTicks(min: number, max: number, count = 4): number[] {
  if (!isFinite(min) || !isFinite(max)) return [];
  if (min === max) {
    min -= 1;
    max += 1;
  }
  const raw = (max - min) / count;
  const mag = Math.pow(10, Math.floor(Math.log10(raw)));
  const step = [1, 2, 2.5, 5, 10].map((m) => m * mag).find((s) => s >= raw) ?? raw;
  const start = Math.floor(min / step) * step;
  const out: number[] = [];
  for (let v = start; v <= max + step * 0.001; v += step) out.push(Math.round(v * 1000) / 1000);
  if (out[out.length - 1] < max) out.push(out[out.length - 1] + step);
  return out;
}

const VN = 'vi-VN';
export const fmtHour = (iso: string) =>
  new Intl.DateTimeFormat(VN, { hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(iso));
export const fmtDay = (iso: string) =>
  new Intl.DateTimeFormat(VN, { day: '2-digit', month: '2-digit' }).format(new Date(iso));
export const fmtDayHour = (iso: string) =>
  new Intl.DateTimeFormat(VN, { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(
    new Date(iso),
  );
export const fmtNum = (v: number | null | undefined, digits = 1) =>
  v == null ? '--' : v.toLocaleString(VN, { minimumFractionDigits: digits, maximumFractionDigits: digits });
