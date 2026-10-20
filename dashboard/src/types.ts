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
  /** Ten cac cua dang tat bao dong. */
  disabledDoors: string[];
}

/** Che do lau dai cua mot cua. */
export type DoorMode = 'OFF' | 'SCHEDULE' | 'ALWAYS';

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
  mode: DoorMode;
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

// ---------------------------------------------------------------- thong ke (mon IoT)

export interface EnvPoint {
  bucket: string;
  tempAvg: number | null;
  tempMin: number | null;
  tempMax: number | null;
  rhAvg: number | null;
  rhMin: number | null;
  rhMax: number | null;
  n: number;
}

export interface EnvSeries {
  hours: number;
  step: string;
  points: EnvPoint[];
}

export interface DoorRef {
  id: string;
  label: string;
}

export interface OpensSeries {
  unit: 'hour' | 'day';
  doors: DoorRef[];
  buckets: string[];
  /** counts[i][j]: so lan mo cua doors[j] tai buckets[i]. */
  counts: number[][];
}

export interface Heatmap {
  days: number;
  /** cells[dow-1][hour], dow 1 = thu Hai. */
  cells: number[][];
  daysPerDow: number[];
  max: number;
}

export interface StateShare {
  deviceId: string;
  label: string;
  openSeconds: number;
  closedSeconds: number;
}

export interface EnvLatest {
  deviceId: string;
  measuredAt: string;
  receivedAt: string;
  temperatureC: number | null;
  humidityPct: number | null;
  ageSeconds: number;
  readingsLastHour: number;
}

// ---------------------------------------------------------------- pin, canh bao moi truong

/** GET /api/stats/device: nhat ky moi lan thuc cua cua chay pin, gom theo buoc. */
export interface DevicePoint {
  bucket: string;
  battAvg: number | null;
  battMin: number | null;
  battMax: number | null;
  workMsAvg: number | null;
  rssiAvg: number | null;
  wakes: number;
}

export interface DeviceSeries {
  deviceId: string;
  hours: number;
  step: string;
  points: DevicePoint[];
}

export type EnvMetric = 'TEMP' | 'RH';
export type EnvDirection = 'HIGH' | 'LOW';

/** Nguong null la khong canh bao chieu do. */
export interface EnvAlertRule {
  enabled: boolean;
  tempHigh: number | null;
  tempLow: number | null;
  rhHigh: number | null;
  rhLow: number | null;
  holdMinutes: number;
}

export interface EnvAlertActive {
  deviceId: string;
  metric: EnvMetric;
  direction: EnvDirection;
  since: string;
  value: number;
  threshold: number | null;
  peak: number;
}

export interface EnvAlerts {
  rule: EnvAlertRule;
  active: EnvAlertActive[];
  tempHysteresis: number;
  rhHysteresis: number;
}

export interface EnvAlertEventRow {
  id: number;
  deviceId: string;
  metric: EnvMetric;
  direction: EnvDirection;
  kind: 'RAISED' | 'CLEARED';
  value: number;
  threshold: number | null;
  occurredAt: string;
}
