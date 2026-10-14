import { useCallback, useEffect, useRef, useState } from 'react';
import type {
  DeviceStatus,
  DoorEvent,
  HourlyCount,
  LinkState,
  RealtimeMessage,
  Stats,
} from './types';
import { api } from './api';

interface LiveData {
  devices: DeviceStatus[];
  events: DoorEvent[];
  hourly: HourlyCount[];
  stats: Stats | null;
  link: LinkState;
  error: string | null;
}

const MAX_LOG_ROWS = 40;
const RECONNECT_BASE_MS = 1000;
const RECONNECT_MAX_MS = 15000;

export function useLive(): LiveData & { refresh: () => void } {
  const [devices, setDevices] = useState<DeviceStatus[]>([]);
  const [events, setEvents] = useState<DoorEvent[]>([]);
  const [hourly, setHourly] = useState<HourlyCount[]>([]);
  const [stats, setStats] = useState<Stats | null>(null);
  const [link, setLink] = useState<LinkState>('connecting');
  const [error, setError] = useState<string | null>(null);

  const socketRef = useRef<WebSocket | null>(null);
  const retryRef = useRef(0);
  const timerRef = useRef<number | null>(null);
  const closedByUs = useRef(false);

  const loadAll = useCallback(async () => {
    try {
      const [devices, evts, hrs, st] = await Promise.all([
        api.devices(),
        api.events(undefined, MAX_LOG_ROWS),
        api.hourly(),
        api.stats(),
      ]);
      setDevices(devices);
      setEvents(evts);
      setHourly(hrs);
      setStats(st);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Không tải được dữ liệu');
    }
  }, []);

  useEffect(() => {
    void loadAll();
  }, [loadAll]);

  useEffect(() => {
    closedByUs.current = false;

    const connect = () => {
      const proto = window.location.protocol === 'https:' ? 'wss' : 'ws';
      const socket = new WebSocket(
        `${proto}://${window.location.host}/ws/live`,
      );
      socketRef.current = socket;

      socket.onopen = () => {
        retryRef.current = 0;
        setLink('live');
        void loadAll();
      };

      socket.onmessage = (frame) => {
        try {
          const msg = JSON.parse(frame.data as string) as RealtimeMessage;
          // Moi tin mang trang thai cua MOT cua: thay dung cua do, giu nguyen cua khac.
          // Truoc 14/10 cho nay ghi de thang, nen giao dien chi hien duoc mot cua.
          const incomingDevice = msg.device;
          if (incomingDevice) {
            setDevices((prev) => {
              const i = prev.findIndex((d) => d.id === incomingDevice.id);
              if (i < 0) {
                return [...prev, incomingDevice].sort((a, b) => a.label.localeCompare(b.label));
              }
              const next = prev.slice();
              next[i] = incomingDevice;
              return next;
            });
          }

          if (msg.type === 'door' && msg.event) {
            const incoming = msg.event;
            setEvents((prev) => {
              if (prev.some((e) => e.id === incoming.id)) return prev;
              return [incoming, ...prev].slice(0, MAX_LOG_ROWS);
            });
            void api
              .hourly()
              .then(setHourly)
              .catch(() => undefined);
            void api
              .stats()
              .then(setStats)
              .catch(() => undefined);
          }
        } catch {}
      };

      socket.onclose = () => {
        if (closedByUs.current) return;
        setLink('retrying');
        const delay = Math.min(
          RECONNECT_BASE_MS * 2 ** retryRef.current,
          RECONNECT_MAX_MS,
        );
        retryRef.current += 1;
        timerRef.current = window.setTimeout(connect, delay);
      };

      socket.onerror = () => {
        socket.close();
      };
    };

    connect();

    return () => {
      closedByUs.current = true;
      if (timerRef.current) window.clearTimeout(timerRef.current);
      socketRef.current?.close();
    };
  }, [loadAll]);

  return { devices, events, hourly, stats, link, error, refresh: loadAll };
}
