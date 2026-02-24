package com.pricealert.alertapi.infrastructure.db.notification;

import com.pricealert.alertapi.domain.notification.Notification;
import com.pricealert.alertapi.domain.notification.NotificationRepository;
import com.pricealert.alertapi.infrastructure.db.notification.mapper.NotificationEntityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class NotificationRepositoryAdapter implements NotificationRepository {

    private final NotificationJpaRepository jpaRepository;
    private final NotificationEntityMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public Page<Notification> findByUserId(String userId, Pageable pageable) {
        return jpaRepository.findByUserId(userId, pageable)
                .map(mapper::toDomain);
    }
}
