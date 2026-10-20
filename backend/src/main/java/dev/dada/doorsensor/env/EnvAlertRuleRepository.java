package dev.dada.doorsensor.env;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvAlertRuleRepository extends JpaRepository<EnvAlertRule, Short> {

    /** Dong duy nhat. V8 da chen san, thieu (CSDL bi xoa tay) thi dung gia tri mac dinh. */
    default EnvAlertRule current() {
        return findById((short) 1).orElseGet(EnvAlertRule::new);
    }
}
