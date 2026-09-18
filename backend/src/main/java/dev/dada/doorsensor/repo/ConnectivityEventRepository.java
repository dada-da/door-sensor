package dev.dada.doorsensor.repo;

import dev.dada.doorsensor.domain.ConnectivityEvent;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConnectivityEventRepository extends JpaRepository<ConnectivityEvent, Long> {

    Page<ConnectivityEvent> findAllByOrderByReceivedAtDesc(Pageable pageable);

    long countByOnlineFalseAndReceivedAtAfter(Instant after);
}
