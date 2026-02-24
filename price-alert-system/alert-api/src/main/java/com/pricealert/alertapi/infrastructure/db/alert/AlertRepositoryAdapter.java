package com.pricealert.alertapi.infrastructure.db.alert;

import com.pricealert.alertapi.domain.alert.Alert;
import com.pricealert.alertapi.domain.alert.AlertRepository;
import com.pricealert.alertapi.infrastructure.db.alert.mapper.AlertEntityMapper;
import com.pricealert.common.event.AlertStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AlertRepositoryAdapter implements AlertRepository {

    private final AlertJpaRepository jpaRepository;
    private final AlertEntityMapper mapper;

    @Override
    @Transactional
    public Alert save(Alert alert) {
        var entity = mapper.toEntity(alert);
        var saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Alert> findById(String id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Alert> findByUserIdAndOptionalFilters(String userId, AlertStatus status, String symbol, Pageable pageable) {
        return jpaRepository.findByUserIdAndOptionalFilters(userId, status, symbol, pageable)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Alert> findBySymbolAndStatus(String symbol, AlertStatus status) {
        return jpaRepository.findBySymbolAndStatus(symbol, status).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public int updateStatusByCurrentStatus(AlertStatus newStatus, AlertStatus currentStatus) {
        return jpaRepository.updateStatusByCurrentStatus(newStatus, currentStatus);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Alert> findByStatus(AlertStatus status) {
        return jpaRepository.findByStatus(status).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
