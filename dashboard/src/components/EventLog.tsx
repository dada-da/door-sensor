import type { DeviceStatus, DoorEvent } from '../types'
import { clockTime, dateTime, durationMs, volts } from '../format'

interface Props {
  events: DoorEvent[]
  /** De doi ma thiet bi ra ten cua. Thieu thi hien ma. */
  devices?: DeviceStatus[]
}

export function EventLog({ events, devices = [] }: Props) {
  const label = (id: string) => devices.find((d) => d.id === id)?.label ?? id

  if (events.length === 0) {
    return (
      <p className="log-empty">
        Chưa có sự kiện nào. Đóng mở cửa một lần, hoặc chạy{' '}
        <code>tools/fake-device.sh</code> để tạo dữ liệu thử.
      </p>
    )
  }

  return (
    <table className="log">
      <thead>
        <tr>
          <th>Thời điểm</th>
          <th>Cửa</th>
          <th>Trạng thái</th>
          <th className="log-num">Kéo dài trước đó</th>
          <th className="log-num log-secondary">Độ trễ</th>
          <th className="log-num log-secondary">Pin</th>
        </tr>
      </thead>
      <tbody>
        {events.map((e) => (
          <tr key={e.id}>
            <td title={dateTime(e.occurredAt)}>{clockTime(e.occurredAt)}</td>
            <td className="log-door" title={e.deviceId}>{label(e.deviceId)}</td>
            <td>
              <span className={`log-state${e.state === 'OPEN' ? ' is-open' : ''}`}>
                {e.state === 'OPEN' ? 'Mở' : 'Đóng'}
              </span>
            </td>
            <td className="log-num">{durationMs(e.durationMs)}</td>
            <td className="log-num log-secondary">{e.lagMs != null ? `${e.lagMs} ms` : '--'}</td>
            <td className="log-num log-secondary">{volts(e.batteryMv)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
