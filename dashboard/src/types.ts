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
