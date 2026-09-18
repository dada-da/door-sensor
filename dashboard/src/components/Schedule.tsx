import type { DeviceStatus } from '../types';
import { clockTime, duration, volts } from '../format';

interface Props {
  device: DeviceStatus | null;
}

export function Schedule({ device }: Props) {
  const isOpen = device?.state === 'OPEN';
  const offline = device !== null && !device.online;

  return (
    <div className="schedule">
      <div className="schedule-headline">
        <span className={`schedule-state${isOpen ? ' is-open' : ''}`}>
          {stateText(device)}
        </span>
        {device?.stateForSeconds != null && device.online && (
          <span className="schedule-elapsed">
            {duration(device.stateForSeconds)}
          </span>
        )}
      </div>

      <Row label="Thiết bị" value={device?.id ?? '--'} muted />
      <Row
        label="Vị trí"
        value={device?.location ?? device?.label ?? '--'}
        muted
      />
      <Row
        label="Đổi trạng thái lúc"
        value={clockTime(device?.stateSince ?? null)}
      />
      <Row
        label="Nhận tin gần nhất"
        value={clockTime(device?.lastSeen ?? null)}
        alert={offline}
      />
      <Row
        label="Pin"
        value={
          device?.batteryMv != null
            ? `${volts(device.batteryMv)}  ${device.batteryPercent ?? '--'}%`
            : '--'
        }
        alert={device?.batteryPercent != null && device.batteryPercent < 20}
      />
      <Row
        label="Cường độ sóng"
        value={device?.rssi != null ? `${device.rssi} dBm` : '--'}
        alert={device?.rssi != null && device.rssi < -80}
      />
      <Row
        label="Số lần khởi động"
        value={device?.bootCount?.toString() ?? '--'}
        muted
      />
    </div>
  );
}

function stateText(device: DeviceStatus | null): string {
  if (!device) return 'Chưa có dữ liệu';
  if (!device.online) return 'Mất liên lạc';
  if (device.state === 'OPEN') return 'Đang mở';
  if (device.state === 'CLOSED') return 'Đang đóng';
  return 'Chưa rõ';
}

function Row({
  label,
  value,
  muted,
  alert,
}: {
  label: string;
  value: string;
  muted?: boolean;
  alert?: boolean;
}) {
  const cls = ['schedule-val', muted ? 'is-muted' : '', alert ? 'is-alert' : '']
    .filter(Boolean)
    .join(' ');

  return (
    <div className="schedule-row">
      <span className="schedule-key">{label}</span>
      <span className="schedule-lead" aria-hidden="true" />
      <span className={cls}>{value}</span>
    </div>
  );
}
