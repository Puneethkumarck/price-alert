package com.pricealert.notifier.domain.persistence;

import com.pricealert.common.event.AlertTrigger;

/**
 * Domain port for idempotent alert trigger log persistence.
 * Infrastructure adapter handles entity mapping and ON CONFLICT logic.
 */
public interface AlertTriggerLogPort {

    void insertIdempotent(AlertTrigger trigger);
}
