package com.pricealert.alertapi.infrastructure.db.triggerlog;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertTriggerLogJpaRepository
        extends JpaRepository<AlertTriggerLogEntity, String> {}
