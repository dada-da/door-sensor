import type { DeviceStatus, DoorEvent, HourlyCount, Stats } from './types';

const BASE = '/api';

async function get<T>(path: string): Promise<T> {
  const res = await fetch(`${BASE}${path}`);
  if (!res.ok) {
    throw new Error(`${res.status} ${res.statusText}`);
  }
  return res.json() as Promise<T>;
}

export const api = {
  devices: () => get<DeviceStatus[]>('/devices'),
  events: (deviceId?: string, size = 40) =>
    get<DoorEvent[]>(
      `/events?size=${size}${deviceId ? `&deviceId=${encodeURIComponent(deviceId)}` : ''}`,
    ),
  hourly: (deviceId?: string) =>
    get<HourlyCount[]>(
      `/stats/hourly${deviceId ? `?deviceId=${encodeURIComponent(deviceId)}` : ''}`,
    ),
  stats: () => get<Stats>('/stats'),
};
