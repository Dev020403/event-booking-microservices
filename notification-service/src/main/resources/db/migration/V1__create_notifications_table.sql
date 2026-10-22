CREATE TABLE notifications (
    id                    UUID PRIMARY KEY,
    user_id               UUID          NOT NULL,
    type                  VARCHAR(50)   NOT NULL,
    title                 VARCHAR(200)  NOT NULL,
    message               VARCHAR(2000) NOT NULL,
    booking_id            UUID,
    -- "<type>:<bookingId>"; makes Kafka replays idempotent
    source_key            VARCHAR(100)  NOT NULL,
    is_read               BOOLEAN       NOT NULL DEFAULT FALSE,
    read_at               TIMESTAMP WITH TIME ZONE,
    recipient_email       VARCHAR(255),
    email_status          VARCHAR(20)   NOT NULL,
    email_attempts        INT           NOT NULL DEFAULT 0,
    email_last_attempt_at TIMESTAMP WITH TIME ZONE,
    email_sent_at         TIMESTAMP WITH TIME ZONE,
    email_last_error      VARCHAR(1000),
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_notifications_source_key UNIQUE (source_key)
);

-- Listing a user's notifications, newest first
CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC);

-- Unread count / unread filter
CREATE INDEX idx_notifications_user_unread ON notifications (user_id) WHERE is_read = FALSE;

-- Email retry job only scans rows that may still need sending
CREATE INDEX idx_notifications_email_due ON notifications (created_at)
    WHERE email_status IN ('PENDING', 'FAILED', 'SENDING');
