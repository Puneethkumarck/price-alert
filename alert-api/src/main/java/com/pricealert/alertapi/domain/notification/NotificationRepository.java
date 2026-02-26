package com.pricealert.alertapi.domain.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationRepository {

    Page<Notification> findByUserId(String userId, Pageable pageable);
}
