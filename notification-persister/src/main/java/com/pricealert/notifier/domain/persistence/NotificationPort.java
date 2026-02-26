package com.pricealert.notifier.domain.persistence;

import com.pricealert.common.event.AlertTrigger;

/**
 * Domain port for idempotent notification persistence.
 * Infrastructure adapter handles entity mapping and ON CONFLICT logic.
 */
public interface NotificationPort {

    void insertIdempotent(AlertTrigger trigger, String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);
}
