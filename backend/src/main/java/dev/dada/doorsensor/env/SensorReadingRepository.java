package dev.dada.doorsensor.env;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SensorReadingRepository extends JpaRepository<SensorReading, Long> {

    List<SensorReading> findByDeviceIdAndMeasuredAtAfterOrderByMeasuredAtAsc(
            String deviceId, Instant after, Pageable pageable);

    List<SensorReading> findByMeasuredAtAfterOrderByMeasuredAtAsc(Instant after, Pageable pageable);

    /** Ban ghi moi nhat cua tung thiet bi. DISTINCT ON la cu phap rieng cua PostgreSQL. */
    @Query(value = """
           SELECT DISTINCT ON (device_id) *
           FROM sensor_reading
           ORDER BY device_id, measured_at DESC
           """, nativeQuery = true)
    List<SensorReading> findLatestPerDevice();

    /** Thap nhat, cao nhat tu mot moc, theo thiet bi. Cho lenh Telegram /nhietdo. */
    interface Range {
        String getDeviceId();
        Double getTempMin();
        Double getTempMax();
        Double getRhMin();
        Double getRhMax();
    }

    @Query(value = """
           SELECT device_id AS "deviceId",
                  MIN(temperature_c) AS "tempMin", MAX(temperature_c) AS "tempMax",
                  MIN(humidity_pct)  AS "rhMin",   MAX(humidity_pct)  AS "rhMax"
           FROM sensor_reading
           WHERE measured_at >= :from
           GROUP BY device_id
           """, nativeQuery = true)
    List<Range> rangeSince(@Param("from") Instant from);

    /** So ban ghi trong mot khoang, de nhin nhanh nhip gui co deu khong. */
    long countByDeviceIdAndReceivedAtAfter(String deviceId, Instant after);
}
