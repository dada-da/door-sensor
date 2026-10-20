package dev.dada.doorsensor.env;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EnvAlertEventRepository extends JpaRepository<EnvAlertEvent, Long> {

    List<EnvAlertEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);

    /**
     * Ban ghi moi nhat cua tung cap (thiet bi, dai luong). Ban ghi do la RAISED
     * nghia la canh bao chua ket thuc truoc lan khoi dong lai.
     */
    @Query(value = """
           SELECT DISTINCT ON (device_id, metric) *
           FROM env_alert_event
           ORDER BY device_id, metric, occurred_at DESC, id DESC
           """, nativeQuery = true)
    List<EnvAlertEvent> findLatestPerDeviceAndMetric();
}
