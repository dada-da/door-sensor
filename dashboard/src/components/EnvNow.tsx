import { useEffect, useState } from 'react';
import { api } from '../api';
import { duration } from '../format';
import type { EnvLatest } from '../types';
import { Gauge } from './stats/Gauge';
import { RH_COLOR, TEMP_COLOR, fmtDayHour } from './stats/chartKit';

/**
 * Nhiet do, do am hien tai (AHT20 tren node coi), dat o trang Tong quan.
 * Hoi /api/env/latest moi 30 giay: node gui moi 60 giay, khong can WebSocket.
 * Duong theo thoi gian van o trang Thong ke.
 */
export function EnvNow() {
  const [latest, setLatest] = useState<EnvLatest[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const load = async () => {
      try {
        setLatest(await api.envLatest());
        setError(null);
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Không tải được số đo');
      }
    };
    void load();
    const t = window.setInterval(() => void load(), 30000);
    return () => window.clearInterval(t);
  }, []);

  const now = latest[0];
  const stale = now && now.ageSeconds > 300 ? `Số đo đã cũ ${duration(now.ageSeconds)}` : undefined;

  return (
    <section className="section">
      <h2 className="section-title">Nhiệt độ và độ ẩm</h2>
      <p className="section-note">
        AHT20 trên node còi{now ? ` (${now.deviceId})` : ''}, gửi mỗi 60 giây.
        {now && ` Số đo lúc ${fmtDayHour(now.measuredAt)}, ${now.readingsLastHour} mẫu trong giờ qua.`}{' '}
        <a href="#thong-ke">Xem biểu đồ theo thời gian</a>
      </p>
      {error && !now && <p className="log-empty">Không tải được số đo: {error}</p>}
      {!error && !now && <p className="log-empty">Chưa nhận được số đo nào từ AHT20.</p>}
      {now && (
        <div className="gauges">
          <Gauge label="Nhiệt độ" value={now.temperatureC} unit="°C" min={10} max={45} color={TEMP_COLOR} track="#f7d3c4" note={stale} />
          <Gauge label="Độ ẩm" value={now.humidityPct} unit="%" min={0} max={100} color={RH_COLOR} track="#cde2fb" note={stale} />
        </div>
      )}
    </section>
  );
}
