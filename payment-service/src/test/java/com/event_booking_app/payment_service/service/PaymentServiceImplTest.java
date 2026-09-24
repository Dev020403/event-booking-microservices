package com.event_booking_app.payment_service.service;

import com.event_booking_app.payment_service.dto.PaymentResponse;
import com.event_booking_app.payment_service.dto.ProcessPaymentRequest;
import com.event_booking_app.payment_service.dto.RefundRequest;
import com.event_booking_app.payment_service.entity.Payment;
import com.event_booking_app.payment_service.entity.PaymentMethod;
import com.event_booking_app.payment_service.entity.PaymentStatus;
import com.event_booking_app.payment_service.exception.PaymentNotFoundException;
import com.event_booking_app.payment_service.exception.PaymentProcessingException;
import com.event_booking_app.payment_service.mapper.PaymentMapper;
import com.event_booking_app.payment_service.repository.PaymentRepository;
import com.event_booking_app.payment_service.strategy.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;

    private PaymentStrategyFactory strategyFactory;
    private PaymentMapper paymentMapper;
    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        CardPaymentStrategy cardStrategy = new CardPaymentStrategy();
        UpiPaymentStrategy upiStrategy = new UpiPaymentStrategy();
        WalletPaymentStrategy walletStrategy = new WalletPaymentStrategy();

        strategyFactory = new PaymentStrategyFactory(List.of(cardStrategy, upiStrategy, walletStrategy));
        paymentMapper = new PaymentMapper();

        paymentService = new PaymentServiceImpl(paymentRepository, strategyFactory, paymentMapper);
    }

    @Test
    @DisplayName("processPayment: should successfully process valid card payment")
    void processPayment_Success() {
        UUID bookingId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();

        ProcessPaymentRequest request = ProcessPaymentRequest.builder()
                .bookingId(bookingId)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .paymentMethod(PaymentMethod.CARD)
                .idempotencyKey(idempotencyKey)
                .build();

        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID());
            }
            return p;
        });

        PaymentResponse response = paymentService.processPayment(request);

        assertThat(response).isNotNull();
        assertThat(response.getBookingId()).isEqualTo(bookingId);
        assertThat(response.getAmount()).isEqualByComparingTo("100.00");
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.getProviderTransactionId()).startsWith("CARD-");

        verify(paymentRepository, times(2)).save(any(Payment.class));
    }

    @Test
    @DisplayName("processPayment: should handle forced card failure (amount = 13.00)")
    void processPayment_ForcedFailure() {
        UUID bookingId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();

        ProcessPaymentRequest request = ProcessPaymentRequest.builder()
                .bookingId(bookingId)
                .amount(new BigDecimal("13.00"))
                .currency("USD")
                .paymentMethod(PaymentMethod.CARD)
                .idempotencyKey(idempotencyKey)
                .build();

        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID());
            }
            return p;
        });

        PaymentResponse response = paymentService.processPayment(request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.getProviderTransactionId()).isNull();
    }

    @Test
    @DisplayName("processPayment: should enforce idempotency and return existing payment")
    void processPayment_Idempotency() {
        UUID bookingId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        UUID existingId = UUID.randomUUID();

        Payment existingPayment = Payment.builder()
                .id(existingId)
                .bookingId(bookingId)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .paymentMethod(PaymentMethod.UPI)
                .status(PaymentStatus.SUCCESS)
                .providerTransactionId("UPI-12345")
                .idempotencyKey(idempotencyKey)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        ProcessPaymentRequest request = ProcessPaymentRequest.builder()
                .bookingId(bookingId)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .paymentMethod(PaymentMethod.UPI)
                .idempotencyKey(idempotencyKey)
                .build();

        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingPayment));

        PaymentResponse response = paymentService.processPayment(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(existingId);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("getPaymentById: should throw PaymentNotFoundException when payment does not exist")
    void getPaymentById_NotFound() {
        UUID id = UUID.randomUUID();
        when(paymentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPaymentById(id))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    @DisplayName("refundPayment: should successfully refund a SUCCESS payment")
    void refundPayment_Success() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .id(paymentId)
                .bookingId(UUID.randomUUID())
                .amount(new BigDecimal("75.00"))
                .currency("USD")
                .paymentMethod(PaymentMethod.WALLET)
                .status(PaymentStatus.SUCCESS)
                .providerTransactionId("WALLET-999")
                .idempotencyKey(UUID.randomUUID())
                .build();

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        RefundRequest refundRequest = RefundRequest.builder().reason("Customer cancelled").build();
        PaymentResponse response = paymentService.refundPayment(paymentId, refundRequest);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    @DisplayName("refundPayment: should throw exception if payment is FAILED")
    void refundPayment_InvalidStatus() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .id(paymentId)
                .bookingId(UUID.randomUUID())
                .amount(new BigDecimal("75.00"))
                .currency("USD")
                .paymentMethod(PaymentMethod.WALLET)
                .status(PaymentStatus.FAILED)
                .idempotencyKey(UUID.randomUUID())
                .build();

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.refundPayment(paymentId, null))
                .isInstanceOf(PaymentProcessingException.class)
                .hasMessageContaining("Cannot refund payment in status: FAILED");
    }
}
