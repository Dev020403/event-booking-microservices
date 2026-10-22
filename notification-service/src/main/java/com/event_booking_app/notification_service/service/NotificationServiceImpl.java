package com.event_booking_app.notification_service.service;

import com.event_booking_app.notification_service.config.NotificationProperties;
import com.event_booking_app.notification_service.dto.NotificationResponse;
import com.event_booking_app.notification_service.entity.EmailStatus;
import com.event_booking_app.notification_service.entity.Notification;
import com.event_booking_app.notification_service.exception.NotificationNotFoundException;
import com.event_booking_app.notification_service.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationProperties properties;

    // Deliberately not @Transactional: the unique-key violation from a concurrent
    // duplicate must be caught here, and catching it inside a transaction would leave
    // that transaction rollback-only. Each repository call runs in its own transaction.
    @Override
    public Optional<Notification> record(NotificationDraft draft) {
        String sourceKey = draft.sourceKey();
        if (notificationRepository.existsBySourceKey(sourceKey)) {
            log.info("Notification {} already exists — skipping replayed event", sourceKey);
            return Optional.empty();
        }

        boolean sendEmail = properties.getEmail().isEnabled() && draft.recipientEmail() != null;
        Notification notification = Notification.builder()
                .userId(draft.userId())
                .bookingId(draft.bookingId())
                .type(draft.type())
                .title(draft.title())
                .message(draft.message())
                .sourceKey(sourceKey)
                .recipientEmail(draft.recipientEmail())
                .emailStatus(sendEmail ? EmailStatus.PENDING : EmailStatus.SKIPPED)
                .build();

        try {
            Notification saved = notificationRepository.saveAndFlush(notification);
            log.info("Created {} notification {} for user {}", saved.getType(), saved.getId(), saved.getUserId());
            return Optional.of(saved);
        } catch (DataIntegrityViolationException ex) {
            log.info("Notification {} was created concurrently — skipping duplicate", sourceKey);
            return Optional.empty();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotifications(UUID userId, boolean unreadOnly, Pageable pageable) {
        Page<Notification> page = unreadOnly
                ? notificationRepository.findByUserIdAndReadFalse(userId, pageable)
                : notificationRepository.findByUserId(userId, pageable);
        return page.map(NotificationResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Override
    @Transactional
    public NotificationResponse markRead(UUID notificationId, UUID userId) {
        // Scoped by owner: another user's notification is reported as not found,
        // so ids can't be probed for existence.
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(Instant.now());
        }
        return NotificationResponse.from(notification);
    }

    @Override
    @Transactional
    public int markAllRead(UUID userId) {
        return notificationRepository.markAllRead(userId, Instant.now());
    }
}
