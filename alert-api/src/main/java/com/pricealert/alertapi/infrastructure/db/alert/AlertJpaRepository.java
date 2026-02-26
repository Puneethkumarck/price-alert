package com.pricealert.alertapi.infrastructure.db.alert;

import com.pricealert.common.event.AlertStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AlertJpaRepository extends JpaRepository<AlertEntity, String> {

    @Query(
            """
            SELECT a FROM AlertEntity a
            WHERE a.userId = :userId
            AND (:status IS NULL OR a.status = :status)
            AND (:symbol IS NULL OR a.symbol = :symbol)
            """)
    Page<AlertEntity> findByUserIdAndOptionalFilters(
            String userId, AlertStatus status, String symbol, Pageable pageable);

    List<AlertEntity> findBySymbolAndStatus(String symbol, AlertStatus status);

    @Modifying
    @Query(
            "UPDATE AlertEntity a SET a.status = :newStatus, a.updatedAt = CURRENT_TIMESTAMP WHERE"
                    + " a.status = :currentStatus")
    int updateStatusByCurrentStatus(AlertStatus newStatus, AlertStatus currentStatus);

    List<AlertEntity> findByStatus(AlertStatus status);
}
