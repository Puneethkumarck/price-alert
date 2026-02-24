package com.pricealert.alertapi.domain.alert;

import com.pricealert.common.event.AlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface AlertRepository {

    Alert save(Alert alert);

    Optional<Alert> findById(String id);

    Page<Alert> findByUserIdAndOptionalFilters(String userId, AlertStatus status, String symbol, Pageable pageable);

    List<Alert> findBySymbolAndStatus(String symbol, AlertStatus status);

    int updateStatusByCurrentStatus(AlertStatus newStatus, AlertStatus currentStatus);

    List<Alert> findByStatus(AlertStatus status);
}
