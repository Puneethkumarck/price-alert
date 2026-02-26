package com.pricealert.notifier.infrastructure.db;

import com.pricealert.common.event.AlertTrigger;
import com.pricealert.common.id.UlidGenerator;
import com.pricealert.notifier.domain.persistence.AlertTriggerLogPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class AlertTriggerLogRepositoryAdapter implements AlertTriggerLogPort {

    private final AlertTriggerLogJpaRepository jpaRepository;

    @Override
    @Transactional
    public void insertIdempotent(AlertTrigger trigger) {
        var row =
                AlertTriggerLogRow.builder()
                        .id(UlidGenerator.generate())
                        .alertId(trigger.alertId())
                        .userId(trigger.userId())
                        .symbol(trigger.symbol())
                        .thresholdPrice(trigger.thresholdPrice())
                        .triggerPrice(trigger.triggerPrice())
                        .tickTimestamp(trigger.tickTimestamp())
                        .triggeredAt(trigger.triggeredAt())
                        .tradingDate(trigger.tradingDate())
                        .build();
        jpaRepository.insertIdempotent(row);
    }
}
