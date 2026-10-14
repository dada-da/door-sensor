import type {
  AlarmSettings,
  AlarmStatus,
  DeviceStatus,
  DoorEvent,
  DoorMode,
  EnvLatest,
  EnvSeries,
  Heatmap,
  OpensSeries,
  StateShare,
  DoorRule,
  DoorRuleUpdate,
  HourlyCount,
  Stats,
} from './types';

const BASE = '/api';

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers: body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  if (res.ok) {
    return res.json() as Promise<T>;
  }
  // Hai ma loi co nghia nghiep vu rieng (AlarmController.respond).
  if (res.status === 409) throw new Error('Còi đang kêu thật, không thử được');
  if (res.status === 503) throw new Error('Không gửi được lệnh tới còi. Còi vẫn tự tắt khi hết giờ.');
  throw new Error(`${res.status} ${res.statusText}`);
}

const get = <T,>(path: string) => request<T>('GET', path);
const id = (s: string) => encodeURIComponent(s);

export const api = {
  devices: () => get<DeviceStatus[]>('/devices'),
  events: (deviceId?: string, size = 40) =>
    get<DoorEvent[]>(`/events?size=${size}${deviceId ? `&deviceId=${id(deviceId)}` : ''}`),
  hourly: (deviceId?: string) =>
    get<HourlyCount[]>(`/stats/hourly${deviceId ? `?deviceId=${id(deviceId)}` : ''}`),
  stats: () => get<Stats>('/stats'),

  alarmStatus: () => get<AlarmStatus>('/alarm/status'),
  alarmStop: () => request<AlarmStatus>('POST', '/alarm/stop'),
  alarmTest: () => request<AlarmStatus>('POST', '/alarm/test'),
  alarmSettings: () => get<AlarmSettings>('/alarm/settings'),
  saveAlarmSettings: (s: AlarmSettings) => request<AlarmSettings>('PUT', '/alarm/settings', s),

  doorRules: () => get<DoorRule[]>('/alarm/doors'),
  saveDoorRule: (deviceId: string, u: DoorRuleUpdate) =>
    request<DoorRule>('PUT', `/alarm/doors/${id(deviceId)}`, u),
  envSeries: (hours: number) => get<EnvSeries>(`/stats/env?hours=${hours}`),
  envLatest: () => get<EnvLatest[]>('/env/latest'),
  opensSeries: (unit: 'hour' | 'day', days: number) =>
    get<OpensSeries>(`/stats/opens?unit=${unit}&days=${days}`),
  heatmap: (days: number) => get<Heatmap>(`/stats/heatmap?days=${days}`),
  stateShare: (days: number) => get<StateShare[]>(`/stats/state-share?days=${days}`),

  setDoorMode: (deviceId: string, mode: DoorMode) =>
    request<DoorRule>('PUT', `/alarm/doors/${id(deviceId)}/mode`, { mode }),
};
