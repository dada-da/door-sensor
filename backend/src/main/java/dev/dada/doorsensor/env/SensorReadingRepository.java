package dev.dada.doorsensor.env;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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

    /** So ban ghi trong mot khoang, de nhin nhanh nhip gui co deu khong. */
    long countByDeviceIdAndReceivedAtAfter(String deviceId, Instant after);
}
