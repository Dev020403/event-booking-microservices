package com.event_booking_app.notification_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {

    private Email email = new Email();

    @Getter
    @Setter
    public static class Email {
        /** When false, notifications are stored in-app only and email is marked SKIPPED. */
        private boolean enabled = true;

        /** Sender address for outgoing email. */
        private String from = "no-reply@event-booking.local";

        /** Total delivery attempts per notification (first send + retries). */
        private int maxAttempts = 5;

        /** How often the retry job runs, and the minimum gap between attempts. */
        private long retryIntervalMs = 60_000;

        /** A send still marked SENDING after this long is assumed lost (e.g. crash) and retried. */
        private long sendingTimeoutMs = 300_000;

        /** Max notifications the retry job picks up per run. */
        private int retryBatchSize = 50;
    }
}
