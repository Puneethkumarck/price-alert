package com.pricealert.alertapi.application.controller.notification;

import com.pricealert.alertapi.application.controller.notification.mapper.NotificationResponseMapper;
import com.pricealert.alertapi.domain.notification.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final NotificationResponseMapper mapper;

    @GetMapping
    public Page<NotificationResponse> listNotifications(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication auth) {
        var userId = auth.getName();
        return notificationRepository.findByUserId(userId, pageable)
                .map(mapper::toResponse);
    }
}
