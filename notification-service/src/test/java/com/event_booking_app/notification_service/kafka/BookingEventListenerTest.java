package com.event_booking_app.notification_service.kafka;

import com.event_booking_app.notification_service.entity.EmailStatus;
import com.event_booking_app.notification_service.entity.Notification;
import com.event_booking_app.notification_service.service.BookingNotificationFactory;
import com.event_booking_app.notification_service.service.EmailDeliveryService;
import com.event_booking_app.notification_service.service.NotificationDraft;
import com.event_booking_app.notification_service.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingEventListenerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private EmailDeliveryService emailDeliveryService;

    private BookingEventListener listener;

    private final UUID bookingId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Real factory: the listener test also covers the JSON -> draft path end to end
        listener = new BookingEventListener(new BookingNotificationFactory(), notificationService, emailDeliveryService);
    }

    private String json(String type, String email) {
        return String.format("{\"eventType\":\"%s\",\"bookingId\":\"%s\",\"userId\":\"%s\",\"userEmail\":%s,\"quantity\":2}",
                type, bookingId, userId, email == null ? "null" : "\"" + email + "\"");
    }

    private Notification saved(EmailStatus status) {
        return Notification.builder().id(UUID.randomUUID()).emailStatus(status).build();
    }

    @Test
    void newNotificationWithEmail_isRecordedAndSent() {
        Notification notification = saved(EmailStatus.PENDING);
        when(notificationService.record(any(NotificationDraft.class))).thenReturn(Optional.of(notification));

        listener.onBookingEvent(json("BOOKING_CONFIRMED", "user@example.com"));

        verify(emailDeliveryService).deliver(notification.getId());
    }

    @Test
    void inAppOnlyNotification_isNotEmailed() {
        when(notificationService.record(any(NotificationDraft.class))).thenReturn(Optional.of(saved(EmailStatus.SKIPPED)));

        listener.onBookingEvent(json("BOOKING_CONFIRMED", null));

        verifyNoInteractions(emailDeliveryService);
    }

    @Test
    void replayedEvent_isNotEmailedAgain() {
        when(notificationService.record(any(NotificationDraft.class))).thenReturn(Optional.empty());

        listener.onBookingEvent(json("BOOKING_CONFIRMED", "user@example.com"));

        verifyNoInteractions(emailDeliveryService);
    }

    @Test
    void malformedJson_isSkippedWithoutRetry() {
        listener.onBookingEvent("{not json");

        verifyNoInteractions(notificationService, emailDeliveryService);
    }

    @Test
    void unknownEventType_isIgnored() {
        listener.onBookingEvent(json("SOMETHING_ELSE", "user@example.com"));

        verifyNoInteractions(notificationService, emailDeliveryService);
    }

    @Test
    void databaseFailure_propagatesForKafkaRetry() {
        when(notificationService.record(any(NotificationDraft.class))).thenThrow(new IllegalStateException("DB down"));

        assertThrows(IllegalStateException.class,
                () -> listener.onBookingEvent(json("BOOKING_CONFIRMED", "user@example.com")));
    }
}
