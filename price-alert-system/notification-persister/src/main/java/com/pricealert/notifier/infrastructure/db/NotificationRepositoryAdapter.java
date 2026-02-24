package com.pricealert.notifier.infrastructure.db;

import com.pricealert.common.event.AlertTrigger;
import com.pricealert.common.id.UlidGenerator;
import com.pricealert.notifier.domain.persistence.NotificationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Repository
@RequiredArgsConstructor
public class NotificationRepositoryAdapter implements NotificationPort {

    private final NotificationJpaRepository jpaRepository;

    @Override
    @Transactional
    public void insertIdempotent(AlertTrigger trigger, String idempotencyKey) {
        var row = NotificationRow.builder()
                .id(UlidGenerator.generate())
                .alertTriggerId(trigger.triggerId())
                .alertId(trigger.alertId())
                .userId(trigger.userId())
                .symbol(trigger.symbol())
                .thresholdPrice(trigger.thresholdPrice())
                .triggerPrice(trigger.triggerPrice())
                .direction(trigger.direction().name())
                .note(trigger.note())
                .idempotencyKey(idempotencyKey)
                .createdAt(Instant.now())
                .read(false)
                .build();
        jpaRepository.insertIdempotent(row);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByIdempotencyKey(String idempotencyKey) {
        return jpaRepository.existsByIdempotencyKey(idempotencyKey);
    }
}
