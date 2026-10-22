package com.event_booking_app.notification_service.service;

import com.event_booking_app.notification_service.config.NotificationProperties;
import com.event_booking_app.notification_service.entity.EmailStatus;
import com.event_booking_app.notification_service.entity.Notification;
import com.event_booking_app.notification_service.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Sends a notification's email. Each attempt first claims the row (atomic status
 * update), so the Kafka listener and the retry job never send the same email at the
 * same time. Failures are recorded, never thrown, and retried by {@link EmailRetryScheduler}.
 *
 * Delivery is at-least-once: if the process dies after the SMTP server accepted the
 * message but before SENT is recorded, the stuck-SENDING recovery will send it again.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailDeliveryService {

    private static final int MAX_ERROR_LENGTH = 1000;

    private final NotificationRepository notificationRepository;
    private final JavaMailSender mailSender;
    private final NotificationProperties properties;

    /**
     * @return true if the email was sent by this call
     */
    public boolean deliver(UUID notificationId) {
        NotificationProperties.Email config = properties.getEmail();
        Instant now = Instant.now();

        int claimed = notificationRepository.claimForSending(
                notificationId,
                EmailStatus.PENDING, EmailStatus.FAILED, EmailStatus.SENDING,
                config.getMaxAttempts(),
                now,
                now.minusMillis(config.getRetryIntervalMs()),
                now.minusMillis(config.getSendingTimeoutMs()));
        if (claimed == 0) {
            // Already sent, being sent elsewhere, not due yet, or out of attempts
            return false;
        }

        Notification notification = notificationRepository.findById(notificationId).orElse(null);
        if (notification == null || notification.getRecipientEmail() == null) {
            notificationRepository.recordSendResult(notificationId, EmailStatus.SKIPPED, null, "No recipient address");
            return false;
        }

        try {
            mailSender.send(buildMessage(notification, config.getFrom()));
            notificationRepository.recordSendResult(notificationId, EmailStatus.SENT, Instant.now(), null);
            log.info("Sent {} email for notification {} (attempt {})",
                    notification.getType(), notificationId, notification.getEmailAttempts());
            return true;
        } catch (RuntimeException ex) {
            // MailException in practice; anything else is also recorded rather than leaving the row SENDING.
            notificationRepository.recordSendResult(notificationId, EmailStatus.FAILED, null, truncate(ex.getMessage()));
            boolean exhausted = notification.getEmailAttempts() >= config.getMaxAttempts();
            // Recipient address is personal data — log the notification id, not the address.
            log.warn("Email for notification {} failed (attempt {}/{}){}: {}",
                    notificationId, notification.getEmailAttempts(), config.getMaxAttempts(),
                    exhausted ? " — giving up" : "", ex.getMessage());
            return false;
        }
    }

    private SimpleMailMessage buildMessage(Notification notification, String from) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(notification.getRecipientEmail());
        message.setSubject(notification.getTitle());
        message.setText(notification.getMessage()
                + "\n\nBooking reference: " + notification.getBookingId()
                + "\n\nThis is an automated message from Event Booking. Please do not reply.");
        return message;
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }
}
