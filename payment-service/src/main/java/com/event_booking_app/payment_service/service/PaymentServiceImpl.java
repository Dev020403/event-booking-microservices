package com.event_booking_app.payment_service.service;

import com.event_booking_app.payment_service.dto.PaymentResponse;
import com.event_booking_app.payment_service.dto.ProcessPaymentRequest;
import com.event_booking_app.payment_service.dto.RefundRequest;
import com.event_booking_app.payment_service.entity.Payment;
import com.event_booking_app.payment_service.entity.PaymentStatus;
import com.event_booking_app.payment_service.exception.PaymentConflictException;
import com.event_booking_app.payment_service.exception.PaymentNotFoundException;
import com.event_booking_app.payment_service.exception.PaymentProcessingException;
import com.event_booking_app.payment_service.mapper.PaymentMapper;
import com.event_booking_app.payment_service.repository.PaymentRepository;
import com.event_booking_app.payment_service.security.SecurityUtils;
import com.event_booking_app.payment_service.strategy.PaymentResult;
import com.event_booking_app.payment_service.strategy.PaymentStrategy;
import com.event_booking_app.payment_service.strategy.PaymentStrategyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final String DEFAULT_CURRENCY = "USD";

    // A booking may have at most one payment in these states at a time.
    private static final Set<PaymentStatus> ACTIVE_STATUSES = EnumSet.of(PaymentStatus.PENDING, PaymentStatus.SUCCESS);
    private static final Set<PaymentStatus> REFUNDABLE_LOOKUP_STATUSES = EnumSet.of(PaymentStatus.SUCCESS, PaymentStatus.REFUNDED);

    private final PaymentRepository paymentRepository;
    private final PaymentStrategyFactory strategyFactory;
    private final PaymentMapper paymentMapper;
    private final SecurityUtils securityUtils;

    @Override
    @Transactional
    public PaymentResponse processPayment(ProcessPaymentRequest request) {
        log.info("Processing payment request for bookingId={}, amount={}, currency={}, method={}, idempotencyKey={}",
                request.getBookingId(), request.getAmount(), request.getCurrency(),
                request.getPaymentMethod(), request.getIdempotencyKey());

        UUID currentUserId = securityUtils.getCurrentUserId();

        // Serialize all charges for this booking. Without this, two requests with
        // different idempotency keys could both pass the checks below and charge twice.
        paymentRepository.lockBooking(request.getBookingId().toString());

        // Idempotency check: return existing payment if already processed with same idempotency key
        Optional<Payment> existingPaymentOpt = paymentRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existingPaymentOpt.isPresent()) {
            Payment existingPayment = existingPaymentOpt.get();
            // A reused key with a different payload (or caller) is a client bug, not a
            // retry — returning the old payment would hide it or leak another user's data.
            if (!existingPayment.getBookingId().equals(request.getBookingId())
                    || existingPayment.getAmount().compareTo(request.getAmount()) != 0
                    || !currentUserId.equals(existingPayment.getUserId())) {
                throw new PaymentConflictException("Idempotency key " + request.getIdempotencyKey()
                        + " was already used for a different payment request");
            }
            log.info("Idempotency key match found. Returning existing payment ID: {} with status: {}",
                    existingPayment.getId(), existingPayment.getStatus());
            return paymentMapper.toResponse(existingPayment);
        }

        if (paymentRepository.existsByBookingIdAndStatusIn(request.getBookingId(), ACTIVE_STATUSES)) {
            throw new PaymentConflictException("Booking " + request.getBookingId()
                    + " already has a pending or successful payment");
        }

        // Initialize new payment
        Payment payment = Payment.builder()
                .bookingId(request.getBookingId())
                .userId(currentUserId)
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : DEFAULT_CURRENCY)
                .paymentMethod(request.getPaymentMethod())
                .status(PaymentStatus.PENDING)
                .idempotencyKey(request.getIdempotencyKey())
                .build();

        // Flush so constraint violations (e.g. a concurrent duplicate idempotency key)
        // fail here, before the charge, rather than at commit after money has moved.
        payment = paymentRepository.saveAndFlush(payment);

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
        assertCanAccess(payment);
        return paymentMapper.toResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByBookingId(UUID bookingId) {
        Payment payment = paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(bookingId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found for bookingId: " + bookingId));
        assertCanAccess(payment);
        return paymentMapper.toResponse(payment);
    }

    @Override
    @Transactional
    public PaymentResponse refundPayment(UUID id, RefundRequest request) {
        Payment payment = paymentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
        assertCanAccess(payment);
        return executeRefund(payment, request);
    }

    @Override
    @Transactional
    public PaymentResponse refundPaymentByBookingId(UUID bookingId, RefundRequest request) {
        // Refund the payment that actually took money (or report the one already refunded),
        // not whichever row happens to be newest — that could be a FAILED retry.
        Payment candidate = paymentRepository
                .findFirstByBookingIdAndStatusInOrderByCreatedAtDesc(bookingId, REFUNDABLE_LOOKUP_STATUSES)
                .orElseThrow(() -> new PaymentNotFoundException("No refundable payment found for bookingId: " + bookingId));
        Payment payment = paymentRepository.findByIdForUpdate(candidate.getId())
                .orElseThrow(() -> new PaymentNotFoundException(candidate.getId()));
        assertCanAccess(payment);
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

    /**
     * Admins can access any payment; everyone else only payments they created.
     * Booking Service forwards the end user's JWT, so its calls pass this check
     * for the booking owner (and for admins cancelling on someone's behalf).
     */
    private void assertCanAccess(Payment payment) {
        if (securityUtils.hasRole("ADMIN")) {
            return;
        }
        if (payment.getUserId() == null || !payment.getUserId().equals(securityUtils.getCurrentUserId())) {
            throw new AccessDeniedException("You do not have permission to access this payment");
        }
    }
}
