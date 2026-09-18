import { DoorPlan } from './components/DoorPlan';
import { Schedule } from './components/Schedule';
import { HourlyChart } from './components/HourlyChart';
import { EventLog } from './components/EventLog';
import { useLive } from './useLive';
import { duration } from './format';

export default function App() {
  const { device, events, hourly, stats, link, error } = useLive();

  return (
    <div className="sheet">
      <header className="masthead">
        <div>
          <h1 className="masthead-title">{device?.label ?? 'Cảm biến cửa'}</h1>
          <p className="masthead-sub">
            Hệ thống nhúng giám sát đóng mở cửa &nbsp;·&nbsp; ESP32 + MQTT
          </p>
        </div>
        <LinkIndicator state={link} />
      </header>

      {error && (
        <p className="notice">
          <span className="notice-strong">Không kết nối được backend.</span>{' '}
          Kiểm tra xem Spring Boot đã chạy ở cổng 8080 chưa, rồi thử tải lại
          trang. Chi tiết: <code>{error}</code>
        </p>
      )}

      {!error && !device && (
        <p className="notice">
          <span className="notice-strong">
            Chưa có thiết bị nào gửi dữ liệu.
          </span>{' '}
          Bật ESP32 lên, hoặc chạy <code>tools/fake-device.sh</code> để giả lập
          một thiết bị.
        </p>
      )}

      <section className="plan-row">
        <DoorPlan
          state={device?.state ?? null}
          online={device?.online ?? false}
        />
        <Schedule device={device} />
      </section>

      <section className="section">
        <h2 className="section-title">Số lần mở theo giờ</h2>
        <p className="section-note">24 giờ gần nhất, tính theo giờ Việt Nam.</p>
        <HourlyChart data={hourly} />
      </section>

      <section className="section">
        <h2 className="section-title">Nhật ký sự kiện</h2>
        <p className="section-note">
          Độ trễ là khoảng cách giữa lúc thiết bị ghi nhận và lúc máy chủ nhận
          được.
        </p>
        <EventLog events={events} />
      </section>

      <TitleBlock
        opensToday={stats?.opensToday ?? null}
        avgLagMs={stats?.avgLagMs ?? null}
        openSeconds={stats?.openSeconds24h ?? null}
        disconnects={stats?.disconnects24h ?? null}
      />
    </div>
  );
}

function LinkIndicator({
  state,
}: {
  state: 'connecting' | 'live' | 'retrying';
}) {
  const text = {
    connecting: 'đang kết nối',
    live: 'đang nhận trực tiếp',
    retrying: 'mất kết nối, đang thử lại',
  }[state];

  return (
    <span className={`link is-${state}`} role="status">
      <span className="link-dot" aria-hidden="true" />
      {text}
    </span>
  );
}

function TitleBlock({
  opensToday,
  avgLagMs,
  openSeconds,
  disconnects,
}: {
  opensToday: number | null;
  avgLagMs: number | null;
  openSeconds: number | null;
  disconnects: number | null;
}) {
  const cells: Array<[string, string]> = [
    ['Số lần mở hôm nay', opensToday?.toString() ?? '--'],
    [
      'Độ trễ trung bình',
      avgLagMs != null ? `${Math.round(avgLagMs)} ms` : '--',
    ],
    [
      'Tổng thời gian mở / 24h',
      openSeconds != null ? duration(openSeconds) : '--',
    ],
    ['Số lần rớt mạng / 24h', disconnects?.toString() ?? '--'],
  ];

  return (
    <div className="titleblock">
      {cells.map(([key, value]) => (
        <div className="titleblock-cell" key={key}>
          <div className="titleblock-key">{key}</div>
          <div className="titleblock-val">{value}</div>
        </div>
      ))}
    </div>
  );
}
