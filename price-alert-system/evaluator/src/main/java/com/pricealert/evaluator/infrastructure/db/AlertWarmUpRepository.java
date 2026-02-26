package com.pricealert.evaluator.infrastructure.db;

import com.pricealert.common.event.AlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AlertWarmUpRepository extends JpaRepository<AlertRow, String> {

    Page<AlertRow> findByStatus(AlertStatus status, Pageable pageable);

    @Modifying
    @Query(
            "UPDATE AlertRow a SET a.status = :newStatus WHERE a.id = :alertId AND a.status ="
                    + " :currentStatus")
    int updateStatusByIdAndCurrentStatus(
            String alertId, AlertStatus newStatus, AlertStatus currentStatus);
}
