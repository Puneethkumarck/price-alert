package com.pricealert.alertapi.infrastructure.db.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationJpaRepository extends JpaRepository<NotificationEntity, String> {

    Page<NotificationEntity> findByUserId(String userId, Pageable pageable);
}
