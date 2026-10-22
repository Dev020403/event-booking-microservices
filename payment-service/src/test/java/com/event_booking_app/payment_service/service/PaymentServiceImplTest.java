package com.event_booking_app.payment_service.service;

import com.event_booking_app.payment_service.dto.PaymentResponse;
import com.event_booking_app.payment_service.dto.ProcessPaymentRequest;
import com.event_booking_app.payment_service.entity.Payment;
import com.event_booking_app.payment_service.entity.PaymentMethod;
import com.event_booking_app.payment_service.entity.PaymentStatus;
import com.event_booking_app.payment_service.exception.PaymentConflictException;
import com.event_booking_app.payment_service.mapper.PaymentMapper;
import com.event_booking_app.payment_service.repository.PaymentRepository;
import com.event_booking_app.payment_service.security.SecurityUtils;
import com.event_booking_app.payment_service.strategy.PaymentResult;
import com.event_booking_app.payment_service.strategy.PaymentStrategy;
import com.event_booking_app.payment_service.strategy.PaymentStrategyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private SecurityUtils securityUtils;

    @Mock
    private PaymentStrategy cardStrategy;

    private PaymentServiceImpl paymentService;

    private final UUID userId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();
    private final UUID idempotencyKey = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(cardStrategy.supports()).thenReturn(PaymentMethod.CARD);
        PaymentStrategyFactory factory = new PaymentStrategyFactory(List.of(cardStrategy));
        paymentService = new PaymentServiceImpl(paymentRepository, factory, new PaymentMapper(), securityUtils);
        lenient().when(securityUtils.getCurrentUserId()).thenReturn(userId);
    }

    private ProcessPaymentRequest request(BigDecimal amount) {
        return ProcessPaymentRequest.builder()
                .bookingId(bookingId)
                .amount(amount)
                .currency("INR")
                .paymentMethod(PaymentMethod.CARD)
                .idempotencyKey(idempotencyKey)
                .build();
    }

    private Payment existingPayment(UUID owner, BigDecimal amount, PaymentStatus status) {
        return Payment.builder()
                .id(UUID.randomUUID())
                .bookingId(bookingId)
                .userId(owner)
                .amount(amount)
                .currency("INR")
                .paymentMethod(PaymentMethod.CARD)
                .status(status)
                .idempotencyKey(idempotencyKey)
                .build();
    }

    @Test
    void processPayment_newPayment_locksBookingAndCharges() {
        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(paymentRepository.existsByBookingIdAndStatusIn(eq(bookingId), anyCollection())).thenReturn(false);
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cardStrategy.charge(any(Payment.class))).thenReturn(PaymentResult.success("CARD-1"));

        PaymentResponse response = paymentService.processPayment(request(new BigDecimal("100.00")));

        assertEquals(PaymentStatus.SUCCESS, response.getStatus());
        verify(paymentRepository).lockBooking(bookingId.toString());
        verify(paymentRepository).saveAndFlush(argThat(p -> userId.equals(p.getUserId())));
    }

    @Test
    void processPayment_bookingAlreadyHasActivePayment_rejectsWithoutCharging() {
        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(paymentRepository.existsByBookingIdAndStatusIn(eq(bookingId), anyCollection())).thenReturn(true);

        assertThrows(PaymentConflictException.class,
                () -> paymentService.processPayment(request(new BigDecimal("100.00"))));
        verify(cardStrategy, never()).charge(any());
    }

    @Test
    void processPayment_sameKeySamePayload_returnsExisting() {
        Payment existing = existingPayment(userId, new BigDecimal("100.00"), PaymentStatus.SUCCESS);
        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existing));

        PaymentResponse response = paymentService.processPayment(request(new BigDecimal("100")));

        assertEquals(existing.getId(), response.getId());
        verify(cardStrategy, never()).charge(any());
    }

    @Test
    void processPayment_sameKeyDifferentAmount_conflicts() {
        Payment existing = existingPayment(userId, new BigDecimal("100.00"), PaymentStatus.SUCCESS);
        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existing));

        assertThrows(PaymentConflictException.class,
                () -> paymentService.processPayment(request(new BigDecimal("1.00"))));
    }

    @Test
    void processPayment_sameKeyOtherUser_conflicts() {
        Payment existing = existingPayment(UUID.randomUUID(), new BigDecimal("100.00"), PaymentStatus.SUCCESS);
        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existing));

        assertThrows(PaymentConflictException.class,
                () -> paymentService.processPayment(request(new BigDecimal("100.00"))));
    }

    @Test
    void refundPayment_otherUsersPayment_isDenied() {
        Payment other = existingPayment(UUID.randomUUID(), new BigDecimal("100.00"), PaymentStatus.SUCCESS);
        when(paymentRepository.findByIdForUpdate(other.getId())).thenReturn(Optional.of(other));
        when(securityUtils.hasRole("ADMIN")).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> paymentService.refundPayment(other.getId(), null));
        verify(cardStrategy, never()).refund(any());
    }

    @Test
    void refundPayment_adminCanRefundAnyPayment() {
        Payment other = existingPayment(UUID.randomUUID(), new BigDecimal("100.00"), PaymentStatus.SUCCESS);
        when(paymentRepository.findByIdForUpdate(other.getId())).thenReturn(Optional.of(other));
        when(securityUtils.hasRole("ADMIN")).thenReturn(true);
        when(cardStrategy.refund(other)).thenReturn(PaymentResult.success("REFUND-1"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = paymentService.refundPayment(other.getId(), null);

        assertEquals(PaymentStatus.REFUNDED, response.getStatus());
    }

    @Test
    void getPaymentById_legacyPaymentWithoutOwner_isAdminOnly() {
        Payment legacy = existingPayment(null, new BigDecimal("100.00"), PaymentStatus.SUCCESS);
        when(paymentRepository.findById(legacy.getId())).thenReturn(Optional.of(legacy));
        when(securityUtils.hasRole("ADMIN")).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> paymentService.getPaymentById(legacy.getId()));
    }
}
