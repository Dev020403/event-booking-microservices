package com.event_booking_app.booking_service.kafka;

import com.event_booking_app.booking_service.entity.Booking;
import com.event_booking_app.booking_service.entity.BookingStatus;
import com.event_booking_app.booking_service.kafka.event.BookingExpiredEvent;
import com.event_booking_app.booking_service.repository.BookingRepository;
import com.event_booking_app.booking_service.service.BookingTransactionSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryEventListenerTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingTransactionSupport txSupport;

    @Mock
    private BookingEventPublisher eventPublisher;

    private ObjectMapper objectMapper = new ObjectMapper();

    private InventoryEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new InventoryEventListener(bookingRepository, txSupport, eventPublisher, objectMapper);
    }

    @Test
    void onInventoryMessage_expiredHold_marksBookingExpired() {
        UUID holdId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Booking booking = Booking.builder()
                .id(bookingId)
                .holdId(holdId)
                .status(BookingStatus.HELD)
                .build();

        when(bookingRepository.findByHoldId(holdId)).thenReturn(Optional.of(booking));
        when(txSupport.transitionStatus(bookingId, BookingStatus.HELD, BookingStatus.EXPIRED)).thenReturn(true);

        String json = String.format("{\"inventoryId\":\"%s\",\"holdId\":\"%s\",\"quantity\":2,\"reason\":\"expired\"}",
                UUID.randomUUID(), holdId);

        listener.onInventoryMessage(json);

        verify(txSupport).transitionStatus(bookingId, BookingStatus.HELD, BookingStatus.EXPIRED);
        verify(eventPublisher).publishBookingExpired(argThat((BookingExpiredEvent e) ->
                bookingId.equals(e.getBookingId()) && BookingExpiredEvent.TYPE.equals(e.getEventType())));
    }

    @Test
    void onInventoryMessage_manualRelease_doesNotMarkExpired() {
        UUID holdId = UUID.randomUUID();
        String json = String.format("{\"inventoryId\":\"%s\",\"holdId\":\"%s\",\"quantity\":2,\"reason\":\"manual release\"}",
                UUID.randomUUID(), holdId);

        listener.onInventoryMessage(json);

        verifyNoInteractions(bookingRepository, txSupport, eventPublisher);
    }
}
