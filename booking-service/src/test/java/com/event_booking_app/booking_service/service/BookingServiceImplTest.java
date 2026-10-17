package com.event_booking_app.booking_service.service;

import com.event_booking_app.booking_service.client.EventClient;
import com.event_booking_app.booking_service.client.InventoryClient;
import com.event_booking_app.booking_service.client.PaymentClient;
import com.event_booking_app.booking_service.client.dto.*;
import com.event_booking_app.booking_service.dto.BookingResponse;
import com.event_booking_app.booking_service.dto.ConfirmPaymentRequest;
import com.event_booking_app.booking_service.dto.CreateBookingRequest;
import com.event_booking_app.booking_service.entity.Booking;
import com.event_booking_app.booking_service.entity.BookingStatus;
import com.event_booking_app.booking_service.exception.BookingNotFoundException;
import com.event_booking_app.booking_service.exception.BookingPaymentFailedException;
import com.event_booking_app.booking_service.exception.InvalidBookingStateException;
import com.event_booking_app.booking_service.kafka.BookingEventPublisher;
import com.event_booking_app.booking_service.kafka.event.BookingCancelledEvent;
import com.event_booking_app.booking_service.kafka.event.BookingConfirmedEvent;
import com.event_booking_app.booking_service.kafka.event.BookingCreatedEvent;
import com.event_booking_app.booking_service.mapper.BookingMapper;
import com.event_booking_app.booking_service.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock
    private BookingRepository bookingRepository;

    @Spy
    private BookingMapper bookingMapper = new BookingMapper();

    @Mock
    private EventClient eventClient;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private PaymentClient paymentClient;

    @Mock
    private BookingEventPublisher eventPublisher;

    @InjectMocks
    private BookingServiceImpl bookingService;

    private UUID userId;
    private UUID eventId;
    private UUID ticketTypeId;
    private UUID inventoryId;
    private UUID holdId;
    private UUID bookingId;
    private UUID idempotencyKey;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        ticketTypeId = UUID.randomUUID();
        inventoryId = UUID.randomUUID();
        holdId = UUID.randomUUID();
        bookingId = UUID.randomUUID();
        idempotencyKey = UUID.randomUUID();
    }

    @Test
    void createBooking_withoutPayment_success() {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .eventId(eventId)
                .ticketTypeId(ticketTypeId)
                .quantity(2)
                .idempotencyKey(idempotencyKey)
                .build();

        TicketTypeResponse ticketType = TicketTypeResponse.builder()
                .id(ticketTypeId)
                .eventId(eventId)
                .name("VIP")
                .price(new BigDecimal("100.00"))
                .build();

        InventoryResponse inventory = InventoryResponse.builder()
                .id(inventoryId)
                .eventId(eventId)
                .ticketTypeId(ticketTypeId)
                .availableQuantity(50)
                .build();

        HoldResponse holdResponse = HoldResponse.builder()
                .holdId(holdId)
                .inventoryId(inventoryId)
                .quantity(2)
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        when(bookingRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(eventClient.getTicketTypeById(ticketTypeId)).thenReturn(ticketType);
        when(inventoryClient.getInventory(eventId, ticketTypeId)).thenReturn(inventory);
        when(inventoryClient.holdInventory(eq(inventoryId), any(HoldRequest.class))).thenReturn(holdResponse);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking b = invocation.getArgument(0);
            b.setId(bookingId);
            return b;
        });

        BookingResponse response = bookingService.createBooking(request, userId.toString());

        assertNotNull(response);
        assertEquals(bookingId, response.getId());
        assertEquals(BookingStatus.HELD, response.getStatus());
        assertEquals(new BigDecimal("200.00"), response.getTotalAmount());

        verify(eventPublisher).publishBookingCreated(any(BookingCreatedEvent.class));
        verifyNoInteractions(paymentClient);
    }

    @Test
    void createBooking_withImmediatePayment_success() {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .eventId(eventId)
                .ticketTypeId(ticketTypeId)
                .quantity(2)
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .idempotencyKey(idempotencyKey)
                .build();

        TicketTypeResponse ticketType = TicketTypeResponse.builder()
                .id(ticketTypeId)
                .eventId(eventId)
                .name("General")
                .price(new BigDecimal("50.00"))
                .build();

        InventoryResponse inventory = InventoryResponse.builder()
                .id(inventoryId)
                .eventId(eventId)
                .ticketTypeId(ticketTypeId)
                .build();

        HoldResponse holdResponse = HoldResponse.builder()
                .holdId(holdId)
                .build();

        UUID paymentId = UUID.randomUUID();
        PaymentResponse paymentResponse = PaymentResponse.builder()
                .id(paymentId)
                .bookingId(bookingId)
                .status(PaymentStatus.COMPLETED)
                .build();

        when(bookingRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(eventClient.getTicketTypeById(ticketTypeId)).thenReturn(ticketType);
        when(inventoryClient.getInventory(eventId, ticketTypeId)).thenReturn(inventory);
        when(inventoryClient.holdInventory(eq(inventoryId), any(HoldRequest.class))).thenReturn(holdResponse);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking b = invocation.getArgument(0);
            if (b.getId() == null) {
                b.setId(bookingId);
            }
            return b;
        });
        when(paymentClient.processPayment(any(ProcessPaymentRequest.class))).thenReturn(paymentResponse);

        BookingResponse response = bookingService.createBooking(request, userId.toString());

        assertNotNull(response);
        assertEquals(BookingStatus.CONFIRMED, response.getStatus());
        assertEquals(paymentId, response.getPaymentId());

        verify(inventoryClient).confirmHold(holdId);
        verify(eventPublisher).publishBookingConfirmed(any(BookingConfirmedEvent.class));
    }

    @Test
    void createBooking_idempotentRequest_returnsExisting() {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .eventId(eventId)
                .ticketTypeId(ticketTypeId)
                .quantity(1)
                .idempotencyKey(idempotencyKey)
                .build();

        Booking existing = Booking.builder()
                .id(bookingId)
                .userId(userId)
                .eventId(eventId)
                .status(BookingStatus.CONFIRMED)
                .idempotencyKey(idempotencyKey)
                .build();

        when(bookingRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existing));

        BookingResponse response = bookingService.createBooking(request, userId.toString());

        assertNotNull(response);
        assertEquals(bookingId, response.getId());
        verifyNoInteractions(eventClient, inventoryClient, paymentClient);
    }

    @Test
    void createBooking_paymentFailure_triggersCompensatingAction() {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .eventId(eventId)
                .ticketTypeId(ticketTypeId)
                .quantity(1)
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .idempotencyKey(idempotencyKey)
                .build();

        TicketTypeResponse ticketType = TicketTypeResponse.builder()
                .id(ticketTypeId)
                .eventId(eventId)
                .price(new BigDecimal("50.00"))
                .build();

        InventoryResponse inventory = InventoryResponse.builder()
                .id(inventoryId)
                .eventId(eventId)
                .ticketTypeId(ticketTypeId)
                .build();

        HoldResponse holdResponse = HoldResponse.builder()
                .holdId(holdId)
                .build();

        when(bookingRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(eventClient.getTicketTypeById(ticketTypeId)).thenReturn(ticketType);
        when(inventoryClient.getInventory(eventId, ticketTypeId)).thenReturn(inventory);
        when(inventoryClient.holdInventory(eq(inventoryId), any(HoldRequest.class))).thenReturn(holdResponse);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking b = invocation.getArgument(0);
            b.setId(bookingId);
            return b;
        });
        when(paymentClient.processPayment(any(ProcessPaymentRequest.class)))
                .thenThrow(new RuntimeException("Card declined"));

        assertThrows(BookingPaymentFailedException.class, () ->
                bookingService.createBooking(request, userId.toString())
        );

        verify(inventoryClient).releaseHold(holdId);
    }

    @Test
    void confirmBookingPayment_success() {
        Booking booking = Booking.builder()
                .id(bookingId)
                .userId(userId)
                .eventId(eventId)
                .ticketTypeId(ticketTypeId)
                .holdId(holdId)
                .quantity(1)
                .totalAmount(new BigDecimal("50.00"))
                .status(BookingStatus.HELD)
                .build();

        UUID paymentId = UUID.randomUUID();
        PaymentResponse paymentResponse = PaymentResponse.builder()
                .id(paymentId)
                .bookingId(bookingId)
                .status(PaymentStatus.COMPLETED)
                .build();

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(paymentClient.processPayment(any(ProcessPaymentRequest.class))).thenReturn(paymentResponse);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ConfirmPaymentRequest request = ConfirmPaymentRequest.builder()
                .paymentMethod(PaymentMethod.UPI)
                .idempotencyKey(UUID.randomUUID())
                .build();

        BookingResponse response = bookingService.confirmBookingPayment(bookingId, request, userId.toString());

        assertEquals(BookingStatus.CONFIRMED, response.getStatus());
        assertEquals(paymentId, response.getPaymentId());
        verify(inventoryClient).confirmHold(holdId);
        verify(eventPublisher).publishBookingConfirmed(any(BookingConfirmedEvent.class));
    }

    @Test
    void cancelBooking_heldBooking_releasesHold() {
        Booking booking = Booking.builder()
                .id(bookingId)
                .userId(userId)
                .eventId(eventId)
                .ticketTypeId(ticketTypeId)
                .holdId(holdId)
                .quantity(2)
                .status(BookingStatus.HELD)
                .build();

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = bookingService.cancelBooking(bookingId, userId.toString(), false);

        assertEquals(BookingStatus.CANCELLED, response.getStatus());
        verify(inventoryClient).releaseHold(holdId);
        verify(eventPublisher).publishBookingCancelled(any(BookingCancelledEvent.class));
    }

    @Test
    void cancelBooking_confirmedBooking_refundsPayment() {
        UUID paymentId = UUID.randomUUID();
        Booking booking = Booking.builder()
                .id(bookingId)
                .userId(userId)
                .eventId(eventId)
                .ticketTypeId(ticketTypeId)
                .paymentId(paymentId)
                .quantity(2)
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = bookingService.cancelBooking(bookingId, userId.toString(), false);

        assertEquals(BookingStatus.CANCELLED, response.getStatus());
        verify(paymentClient).refundPayment(eq(paymentId), any(RefundRequest.class));
        verify(eventPublisher).publishBookingCancelled(any(BookingCancelledEvent.class));
    }

    @Test
    void cancelBooking_wrongUser_throwsAccessDenied() {
        Booking booking = Booking.builder()
                .id(bookingId)
                .userId(UUID.randomUUID()) // different user
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThrows(AccessDeniedException.class, () ->
                bookingService.cancelBooking(bookingId, userId.toString(), false)
        );
    }

    @Test
    void getBookingById_success() {
        Booking booking = Booking.builder()
                .id(bookingId)
                .userId(userId)
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        BookingResponse response = bookingService.getBookingById(bookingId, userId.toString(), false);

        assertNotNull(response);
        assertEquals(bookingId, response.getId());
    }

    @Test
    void getUserBookings_success() {
        Pageable pageable = PageRequest.of(0, 10);
        Booking booking = Booking.builder().id(bookingId).userId(userId).status(BookingStatus.CONFIRMED).build();
        Page<Booking> page = new PageImpl<>(List.of(booking), pageable, 1);

        when(bookingRepository.findByUserId(userId, pageable)).thenReturn(page);

        Page<BookingResponse> result = bookingService.getUserBookings(userId.toString(), null, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(bookingId, result.getContent().get(0).getId());
    }

    @Test
    void getAllBookings_success() {
        Pageable pageable = PageRequest.of(0, 10);
        Booking booking = Booking.builder().id(bookingId).userId(userId).status(BookingStatus.CONFIRMED).build();
        Page<Booking> page = new PageImpl<>(List.of(booking), pageable, 1);

        when(bookingRepository.findAll(pageable)).thenReturn(page);

        Page<BookingResponse> result = bookingService.getAllBookings(null, pageable);

        assertEquals(1, result.getTotalElements());
    }
}
