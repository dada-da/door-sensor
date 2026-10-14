import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from './api';
import type { AlarmSettings, AlarmStatus, DoorRule } from './types';

/**
 * Trang thai bao dong, hoi lai moi 5 giay.
 *
 * Khong di qua WebSocket: tin realtime hien tai luon mang "device", them loai tin
 * moi se phai sua ca backend lan useLive. 5 giay du nhanh cho bang dieu khien,
 * con canh bao gap da co Telegram.
 */
const POLL_MS = 5000;

export function useAlarm() {
  const [status, setStatus] = useState<AlarmStatus | null>(null);
  const [doors, setDoors] = useState<DoorRule[]>([]);
  const [settings, setSettings] = useState<AlarmSettings | null>(null);
  const [error, setError] = useState<string | null>(null);
  const timer = useRef<number | null>(null);

  const load = useCallback(async () => {
    try {
      const [s, d, st] = await Promise.all([api.alarmStatus(), api.doorRules(), api.alarmSettings()]);
      setStatus(s);
      setDoors(d);
      setSettings(st);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Không tải được trạng thái báo động');
    }
  }, []);

  useEffect(() => {
    void load();
    timer.current = window.setInterval(() => void load(), POLL_MS);
    return () => {
      if (timer.current) window.clearInterval(timer.current);
    };
  }, [load]);

  /** Chay mot thao tac, tai lai ngay sau do de giao dien khong phai cho 5 giay. */
  const run = useCallback(
    async (action: () => Promise<unknown>): Promise<string | null> => {
      try {
        await action();
        await load();
        return null;
      } catch (e) {
        await load();
        return e instanceof Error ? e.message : 'Thao tác thất bại';
      }
    },
    [load],
  );

  return { status, doors, settings, error, run, reload: load };
}
