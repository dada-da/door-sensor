package dev.dada.doorsensor.repo;

import dev.dada.doorsensor.domain.Device;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceRepository extends JpaRepository<Device, String> {

    List<Device> findAllByOrderByLabelAsc();

    @Query("SELECT d FROM Device d WHERE d.online = true AND (d.lastSeen IS NULL OR d.lastSeen < :threshold)")
    List<Device> findStale(@Param("threshold") Instant threshold);
}
