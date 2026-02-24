package com.pricealert.evaluator.infrastructure.db;

import com.pricealert.common.event.AlertStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * JPA repository used by the evaluator for warm-up (load ACTIVE alerts)
 * and async status updates (ACTIVE → TRIGGERED_TODAY).
 */
public interface AlertWarmUpRepository extends JpaRepository<AlertRow, String> {

    List<AlertRow> findByStatus(AlertStatus status);

    @Modifying
    @Query("UPDATE AlertRow a SET a.status = :newStatus WHERE a.id = :alertId AND a.status = :currentStatus")
    int updateStatusByIdAndCurrentStatus(String alertId, AlertStatus newStatus, AlertStatus currentStatus);
}
