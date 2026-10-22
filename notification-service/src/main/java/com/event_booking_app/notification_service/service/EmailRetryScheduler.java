package com.event_booking_app.notification_service.service;

import com.event_booking_app.notification_service.config.NotificationProperties;
import com.event_booking_app.notification_service.entity.EmailStatus;
import com.event_booking_app.notification_service.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Retries emails that failed, were never attempted (e.g. the service stopped between
 * saving the notification and sending), or got stuck mid-send. Safe to run on every
 * instance: {@link EmailDeliveryService} claims each row atomically before sending.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailRetryScheduler {

    private final NotificationRepository notificationRepository;
    private final EmailDeliveryService emailDeliveryService;
    private final NotificationProperties properties;

    @Scheduled(fixedDelayString = "${notification.email.retry-interval-ms:60000}",
            initialDelayString = "${notification.email.retry-interval-ms:60000}")
    public void retryDueEmails() {
        NotificationProperties.Email config = properties.getEmail();
        if (!config.isEnabled()) {
            return;
        }

        Instant now = Instant.now();
        List<UUID> due = notificationRepository.findIdsDueForEmail(
                EnumSet.of(EmailStatus.PENDING, EmailStatus.FAILED),
                EmailStatus.SENDING,
                config.getMaxAttempts(),
                now.minusMillis(config.getRetryIntervalMs()),
                now.minusMillis(config.getSendingTimeoutMs()),
                PageRequest.of(0, config.getRetryBatchSize()));

        if (due.isEmpty()) {
            return;
        }

        log.info("Retrying {} notification email(s)", due.size());
        for (UUID id : due) {
            try {
                emailDeliveryService.deliver(id);
            } catch (Exception ex) {
                // One bad row shouldn't stop the batch; it stays due and is retried next run.
                log.error("Unexpected error retrying email for notification {}", id, ex);
            }
        }
    }
}
