import { DoorCard } from './components/DoorCard';
import { EnvCard } from './components/EnvCard';
import { HourlyChart } from './components/HourlyChart';
import { EventLog } from './components/EventLog';
import { AlarmPanel } from './components/AlarmPanel';
import { StatsPage } from './components/stats/StatsPage';
import { useEffect, useState } from 'react';
import { useLive } from './useLive';
import { useAlarm } from './useAlarm';
import { duration } from './format';

export default function App() {
  const { devices, events, hourly, stats, link, error } = useLive();
  const alarm = useAlarm();
  const page = useHashPage();

  return (
    <div className="sheet">
      <header className="masthead">
        <div>
          <h1 className="masthead-title">Giám sát cửa</h1>
          <p className="masthead-sub">
            Hệ thống nhúng giám sát đóng mở cửa và báo động &nbsp;·&nbsp; ESP32 + MQTT
          </p>
        </div>
        <LinkIndicator state={link} />
      </header>

      <nav className="tabs" aria-label="Trang">
        <a href="#" className={`tab${page === 'home' ? ' is-on' : ''}`} aria-current={page === 'home' ? 'page' : undefined}>
          Tổng quan
        </a>
        <a href="#thong-ke" className={`tab${page === 'stats' ? ' is-on' : ''}`} aria-current={page === 'stats' ? 'page' : undefined}>
          Thống kê
        </a>
      </nav>

      {page === 'stats' && <StatsPage />}
      {page === 'home' && (
      <>

      {error && (
        <p className="notice">
          <span className="notice-strong">Không kết nối được backend.</span>{' '}
          Kiểm tra xem Spring Boot đã chạy ở cổng 8080 chưa, rồi thử tải lại
          trang. Chi tiết: <code>{error}</code>
        </p>
      )}

      {!error && devices.length === 0 && (
        <p className="notice">
          <span className="notice-strong">
            Chưa có thiết bị nào gửi dữ liệu.
          </span>{' '}
          Bật ESP32 lên, hoặc chạy <code>tools/fake-device.sh</code> để giả lập
          một thiết bị.
        </p>
      )}

      <TitleBlock
        opensToday={stats?.opensToday ?? null}
        avgLagMs={stats?.avgLagMs ?? null}
        openSeconds={stats?.openSeconds24h ?? null}
        disconnects={stats?.disconnects24h ?? null}
      />

      {/* Hang tren: moi cua mot the, them the moi truong. Luoi tu xuong dong theo be rong. */}
      <div className="overview">
        {devices.map((device) => (
          <DoorCard key={device.id} device={device} rule={alarm.doors.find((r) => r.deviceId === device.id)} />
        ))}
        <EnvCard />
      </div>

      {/* Hai cot: dieu khien bao dong ben trai, lich su ben phai. Man hinh hep thi xep doc. */}
      <div className="split">
        <AlarmPanel
          status={alarm.status}
          doors={alarm.doors}
          settings={alarm.settings}
          error={alarm.error}
          run={alarm.run}
        />

        <div>
          <section className="section">
            <h2 className="section-title">Số lần mở theo giờ</h2>
            <p className="section-note">24 giờ gần nhất, mọi cửa, tính theo giờ Việt Nam.</p>
            <HourlyChart data={hourly} />
          </section>

          <section className="section">
            <h2 className="section-title">Nhật ký sự kiện</h2>
            <p className="section-note">
              Độ trễ là khoảng cách giữa lúc thiết bị ghi nhận và lúc máy chủ nhận được.
            </p>
            <div className="log-scroll">
              <EventLog events={events} devices={devices} />
            </div>
          </section>
        </div>
      </div>
      </>
      )}
    </div>
  );
}

/** Hai trang, chuyen bang #: khong them thu vien router cho hai duong dan. */
function useHashPage(): 'home' | 'stats' {
  const read = () => (window.location.hash === '#thong-ke' ? 'stats' : 'home');
  const [page, setPage] = useState<'home' | 'stats'>(read);
  useEffect(() => {
    const on = () => setPage(read());
    window.addEventListener('hashchange', on);
    return () => window.removeEventListener('hashchange', on);
  }, []);
  return page;
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
