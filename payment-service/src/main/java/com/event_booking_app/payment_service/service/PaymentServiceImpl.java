package com.event_booking_app.payment_service.service;

import com.event_booking_app.payment_service.dto.PaymentResponse;
import com.event_booking_app.payment_service.dto.ProcessPaymentRequest;
import com.event_booking_app.payment_service.dto.RefundRequest;
import com.event_booking_app.payment_service.entity.Payment;
import com.event_booking_app.payment_service.entity.PaymentStatus;
import com.event_booking_app.payment_service.exception.PaymentNotFoundException;
import com.event_booking_app.payment_service.exception.PaymentProcessingException;
import com.event_booking_app.payment_service.mapper.PaymentMapper;
import com.event_booking_app.payment_service.repository.PaymentRepository;
import com.event_booking_app.payment_service.strategy.PaymentResult;
import com.event_booking_app.payment_service.strategy.PaymentStrategy;
import com.event_booking_app.payment_service.strategy.PaymentStrategyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentStrategyFactory strategyFactory;
    private final PaymentMapper paymentMapper;

    @Override
    @Transactional
    public PaymentResponse processPayment(ProcessPaymentRequest request) {
        log.info("Processing payment request for bookingId={}, amount={}, currency={}, method={}, idempotencyKey={}",
                request.getBookingId(), request.getAmount(), request.getCurrency(),
                request.getPaymentMethod(), request.getIdempotencyKey());

        // Idempotency check: return existing payment if already processed with same idempotency key
        Optional<Payment> existingPaymentOpt = paymentRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existingPaymentOpt.isPresent()) {
            Payment existingPayment = existingPaymentOpt.get();
            log.info("Idempotency key match found. Returning existing payment ID: {} with status: {}",
                    existingPayment.getId(), existingPayment.getStatus());
            return paymentMapper.toResponse(existingPayment);
        }

        // Initialize new payment
        Payment payment = Payment.builder()
                .bookingId(request.getBookingId())
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
                .paymentMethod(request.getPaymentMethod())
                .status(PaymentStatus.PENDING)
                .idempotencyKey(request.getIdempotencyKey())
                .build();

        payment = paymentRepository.save(payment);

        // Resolve payment strategy and execute charge
        PaymentStrategy strategy = strategyFactory.resolve(request.getPaymentMethod());
        PaymentResult result = strategy.charge(payment);

        if (result.success()) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setProviderTransactionId(result.providerTransactionId());
            log.info("Payment succeeded for bookingId={}. ProviderTxId={}",
                    request.getBookingId(), result.providerTransactionId());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            log.warn("Payment failed for bookingId={}. Reason: {}",
                    request.getBookingId(), result.failureReason());
        }

        Payment savedPayment = paymentRepository.save(payment);
        return paymentMapper.toResponse(savedPayment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(UUID id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
        return paymentMapper.toResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByBookingId(UUID bookingId) {
        Payment payment = paymentRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found for bookingId: " + bookingId));
        return paymentMapper.toResponse(payment);
    }

    @Override
    @Transactional
    public PaymentResponse refundPayment(UUID id, RefundRequest request) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
        return executeRefund(payment, request);
    }

    @Override
    @Transactional
    public PaymentResponse refundPaymentByBookingId(UUID bookingId, RefundRequest request) {
        Payment payment = paymentRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found for bookingId: " + bookingId));
        return executeRefund(payment, request);
    }

    private PaymentResponse executeRefund(Payment payment, RefundRequest request) {
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            log.info("Payment ID: {} is already refunded", payment.getId());
            return paymentMapper.toResponse(payment);
        }

        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new PaymentProcessingException("Cannot refund payment in status: " + payment.getStatus());
        }

        String reason = (request != null && request.getReason() != null) ? request.getReason() : "Customer refund request";
        log.info("Refunding payment ID: {} for bookingId: {}. Reason: {}", payment.getId(), payment.getBookingId(), reason);

        PaymentStrategy strategy = strategyFactory.resolve(payment.getPaymentMethod());
        PaymentResult result = strategy.refund(payment);

        if (result.success()) {
            payment.setStatus(PaymentStatus.REFUNDED);
            log.info("Payment ID: {} successfully refunded", payment.getId());
        } else {
            log.error("Failed to refund payment ID: {}. Reason: {}", payment.getId(), result.failureReason());
            throw new PaymentProcessingException("Refund failed: " + result.failureReason());
        }

        Payment savedPayment = paymentRepository.save(payment);
        return paymentMapper.toResponse(savedPayment);
    }
}
