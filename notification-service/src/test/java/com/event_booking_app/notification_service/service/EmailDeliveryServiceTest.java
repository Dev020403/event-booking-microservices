package com.event_booking_app.notification_service.service;

import com.event_booking_app.notification_service.config.NotificationProperties;
import com.event_booking_app.notification_service.entity.EmailStatus;
import com.event_booking_app.notification_service.entity.Notification;
import com.event_booking_app.notification_service.entity.NotificationType;
import com.event_booking_app.notification_service.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailDeliveryServiceTest {

    @Mock
    private NotificationRepository repository;

    @Mock
    private JavaMailSender mailSender;

    private EmailDeliveryService service;

    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new EmailDeliveryService(repository, mailSender, new NotificationProperties());
    }

    private void claimReturns(int rows) {
        when(repository.claimForSending(eq(id), eq(EmailStatus.PENDING), eq(EmailStatus.FAILED),
                eq(EmailStatus.SENDING), anyInt(), any(), any(), any())).thenReturn(rows);
    }

    private Notification notification(String email) {
        return Notification.builder()
                .id(id).userId(UUID.randomUUID()).bookingId(UUID.randomUUID())
                .type(NotificationType.BOOKING_CONFIRMED)
                .title("Booking #ABC confirmed").message("Your booking is confirmed.")
                .recipientEmail(email).emailStatus(EmailStatus.SENDING).emailAttempts(1)
                .build();
    }

    @Test
    void notClaimed_doesNotSend() {
        claimReturns(0);

        assertFalse(service.deliver(id));
        verifyNoInteractions(mailSender);
    }

    @Test
    void success_sendsAndRecordsSent() {
        claimReturns(1);
        when(repository.findById(id)).thenReturn(Optional.of(notification("user@example.com")));

        assertTrue(service.deliver(id));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertArrayEquals(new String[]{"user@example.com"}, captor.getValue().getTo());
        assertEquals("Booking #ABC confirmed", captor.getValue().getSubject());
        verify(repository).recordSendResult(eq(id), eq(EmailStatus.SENT), any(), isNull());
    }

    @Test
    void mailFailure_recordsFailedWithoutThrowing() {
        claimReturns(1);
        when(repository.findById(id)).thenReturn(Optional.of(notification("user@example.com")));
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertFalse(service.deliver(id));
        verify(repository).recordSendResult(eq(id), eq(EmailStatus.FAILED), isNull(), eq("SMTP down"));
    }

    @Test
    void noRecipient_isSkipped() {
        claimReturns(1);
        when(repository.findById(id)).thenReturn(Optional.of(notification(null)));

        assertFalse(service.deliver(id));
        verifyNoInteractions(mailSender);
        verify(repository).recordSendResult(eq(id), eq(EmailStatus.SKIPPED), isNull(), any());
    }
}
