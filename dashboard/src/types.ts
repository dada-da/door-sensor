export type DoorState = 'OPEN' | 'CLOSED';

export interface DeviceStatus {
  id: string;
  label: string;
  location: string | null;
  online: boolean;
  state: DoorState | null;
  stateSince: string | null;
  stateForSeconds: number | null;
  lastSeen: string | null;
  batteryMv: number | null;
  batteryPercent: number | null;
  rssi: number | null;
  bootCount: number | null;
}

export interface DoorEvent {
  id: number;
  deviceId: string;
  state: DoorState;
  occurredAt: string;
  receivedAt: string;
  lagMs: number | null;
  durationMs: number | null;
  batteryMv: number | null;
  rssi: number | null;
}

export interface HourlyCount {
  hour: string;
  opens: number;
}

export interface Stats {
  opensToday: number;
  opens24h: number;
  avgLagMs: number;
  openSeconds24h: number;
  disconnects24h: number;
}

export interface RealtimeMessage {
  type: 'door' | 'device';
  device: DeviceStatus;
  event?: DoorEvent;
}

export type LinkState = 'connecting' | 'live' | 'retrying';

/** GET /api/alarm/status. level ARMED_SIREN_OFFLINE la trang thai nguy hiem nhat. */
export type AlarmLevel = 'DISABLED' | 'DISARMED' | 'ARMED' | 'ARMED_SIREN_OFFLINE';

export interface AlarmStatus {
  enabled: boolean;
  armed: boolean;
  level: AlarmLevel;
  sirenId: string;
  sirenLink: 'UNKNOWN' | 'ONLINE' | 'OFFLINE';
  sirenLastSeen: string | null;
  sirenActive: boolean;
  sirenActiveUntil: string | null;
}

export type OverrideMode = 'NONE' | 'ARMED' | 'DISARMED';

/** GET /api/alarm/doors: luat bao dong rieng cua mot cua. */
export interface DoorRule {
  deviceId: string;
  label: string;
  online: boolean;
  doorState: DoorState | null;
  sirenEnabled: boolean;
  /** "HH:mm" hoac "HH:mm:ss" tu Jackson. */
  startTime: string;
  endTime: string;
  /** 1 = thu Hai ... 7 = Chu nhat (ISO-8601). */
  days: number[];
  overrideMode: OverrideMode;
  overrideUntil: string | null;
  armedNow: boolean;
  inWindowNow: boolean;
}

export interface DoorRuleUpdate {
  sirenEnabled?: boolean;
  startTime?: string;
  endTime?: string;
  days?: number[];
}

export interface AlarmSettings {
  sirenSeconds: number;
}
