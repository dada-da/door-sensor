package dev.dada.doorsensor.repo;

import dev.dada.doorsensor.domain.DoorEvent;
import dev.dada.doorsensor.domain.DoorState;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DoorEventRepository extends JpaRepository<DoorEvent, Long> {

    Page<DoorEvent> findByDeviceIdOrderByOccurredAtDesc(String deviceId, Pageable pageable);

    Page<DoorEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);

    long countByDeviceIdAndOccurredAtAfter(String deviceId, Instant after);

    long countByOccurredAtAfter(Instant after);

    long countByStateAndOccurredAtAfter(DoorState state, Instant after);

    @Query(value = """
           SELECT EXISTS (
               SELECT 1 FROM door_event
               WHERE device_id = :deviceId
                 AND state = :state
                 AND occurred_at BETWEEN :from AND :to
           )
           """, nativeQuery = true)
    boolean existsDuplicate(@Param("deviceId") String deviceId,
                            @Param("state") String state,
                            @Param("from") Instant from,
                            @Param("to") Instant to);

    @Query(value = """
           SELECT date_trunc('hour', occurred_at) AS bucket,
                  COUNT(*)                        AS total
           FROM door_event
           WHERE state = 'OPEN'
             AND occurred_at >= :from
             AND (:deviceId = '' OR device_id = :deviceId)
           GROUP BY bucket
           ORDER BY bucket
           """, nativeQuery = true)
    List<Object[]> countOpensByHour(@Param("from") Instant from,
                                    @Param("deviceId") String deviceId);

    @Query(value = """
           SELECT CAST(COALESCE(AVG(EXTRACT(EPOCH FROM (received_at - occurred_at)) * 1000), 0)
                       AS DOUBLE PRECISION)
           FROM door_event
           WHERE occurred_at >= :from
           """, nativeQuery = true)
    Double averageLagMs(@Param("from") Instant from);

    @Query(value = """
           SELECT CAST(COALESCE(SUM(duration_ms), 0) AS BIGINT)
           FROM door_event
           WHERE state = 'CLOSED'
             AND duration_ms IS NOT NULL
             AND occurred_at >= :from
           """, nativeQuery = true)
    Long totalOpenMillis(@Param("from") Instant from);
}
