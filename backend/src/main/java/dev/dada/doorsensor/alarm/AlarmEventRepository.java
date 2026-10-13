package dev.dada.doorsensor.alarm;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlarmEventRepository extends JpaRepository<AlarmEvent, Long> {

    /** Cac dot coi chua dong, dung de khoi phuc trang thai sau khi may chu khoi dong lai. */
    List<AlarmEvent> findByStoppedAtIsNull();

    /** Lenh gan nhat chua duoc node coi xac nhan, de gan moc ackedAt. */
    Optional<AlarmEvent> findFirstBySirenIdAndAckedAtIsNullAndCommandOkTrueAndTriggeredAtAfterOrderByTriggeredAtDesc(
            String sirenId, Instant after);

    List<AlarmEvent> findAllByOrderByTriggeredAtDesc(Pageable pageable);
}
