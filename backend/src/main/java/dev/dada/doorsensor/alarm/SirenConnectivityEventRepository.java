package dev.dada.doorsensor.alarm;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SirenConnectivityEventRepository extends JpaRepository<SirenConnectivityEvent, Long> {
}
