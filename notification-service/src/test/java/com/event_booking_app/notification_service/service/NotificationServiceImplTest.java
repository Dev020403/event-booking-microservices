package com.event_booking_app.notification_service.service;

import com.event_booking_app.notification_service.config.NotificationProperties;
import com.event_booking_app.notification_service.entity.EmailStatus;
import com.event_booking_app.notification_service.entity.Notification;
import com.event_booking_app.notification_service.entity.NotificationType;
import com.event_booking_app.notification_service.exception.NotificationNotFoundException;
import com.event_booking_app.notification_service.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository repository;

    private NotificationProperties properties;
    private NotificationServiceImpl service;

    private final UUID userId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties();
        service = new NotificationServiceImpl(repository, properties);
    }

    private NotificationDraft draft(String email) {
        return new NotificationDraft(userId, bookingId, NotificationType.BOOKING_CONFIRMED, "t", "m", email);
    }

    private Notification captureSaved() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    @Test
    void record_withEmail_isPendingEmail() {
        when(repository.existsBySourceKey(any())).thenReturn(false);
        when(repository.saveAndFlush(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<Notification> result = service.record(draft("user@example.com"));

        assertTrue(result.isPresent());
        Notification saved = captureSaved();
        assertEquals(EmailStatus.PENDING, saved.getEmailStatus());
        assertEquals("BOOKING_CONFIRMED:" + bookingId, saved.getSourceKey());
        assertFalse(saved.isRead());
    }

    @Test
    void record_withoutEmail_isInAppOnly() {
        when(repository.existsBySourceKey(any())).thenReturn(false);
        when(repository.saveAndFlush(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        service.record(draft(null));

        assertEquals(EmailStatus.SKIPPED, captureSaved().getEmailStatus());
    }

    @Test
    void record_emailDisabled_isInAppOnly() {
        properties.getEmail().setEnabled(false);
        when(repository.existsBySourceKey(any())).thenReturn(false);
        when(repository.saveAndFlush(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        service.record(draft("user@example.com"));

        assertEquals(EmailStatus.SKIPPED, captureSaved().getEmailStatus());
    }

    @Test
    void record_replayedEvent_isSkipped() {
        when(repository.existsBySourceKey("BOOKING_CONFIRMED:" + bookingId)).thenReturn(true);

        assertTrue(service.record(draft("user@example.com")).isEmpty());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void record_concurrentDuplicate_isSkipped() {
        when(repository.existsBySourceKey(any())).thenReturn(false);
        when(repository.saveAndFlush(any(Notification.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertTrue(service.record(draft("user@example.com")).isEmpty());
    }

    @Test
    void markRead_otherUsersNotification_isNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThrows(NotificationNotFoundException.class, () -> service.markRead(id, userId));
    }

    @Test
    void markRead_setsReadAndTimestamp() {
        UUID id = UUID.randomUUID();
        Notification notification = Notification.builder()
                .id(id).userId(userId).type(NotificationType.BOOKING_CREATED)
                .title("t").message("m").emailStatus(EmailStatus.SENT).build();
        when(repository.findByIdAndUserId(id, userId)).thenReturn(Optional.of(notification));

        var response = service.markRead(id, userId);

        assertTrue(response.read());
        assertNotNull(response.readAt());
    }
}
