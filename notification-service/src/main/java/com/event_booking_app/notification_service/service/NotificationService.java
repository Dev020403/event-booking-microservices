package com.event_booking_app.notification_service.service;

import com.event_booking_app.notification_service.dto.NotificationResponse;
import com.event_booking_app.notification_service.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface NotificationService {

    /**
     * Persists a notification for the draft.
     *
     * @return the new notification, or empty if one already exists for this
     *         booking and type (e.g. a replayed Kafka message)
     */
    Optional<Notification> record(NotificationDraft draft);

    Page<NotificationResponse> getNotifications(UUID userId, boolean unreadOnly, Pageable pageable);

    long getUnreadCount(UUID userId);

    NotificationResponse markRead(UUID notificationId, UUID userId);

    int markAllRead(UUID userId);
}
