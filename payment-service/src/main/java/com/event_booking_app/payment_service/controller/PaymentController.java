package com.event_booking_app.payment_service.controller;

import com.event_booking_app.payment_service.dto.PaymentResponse;
import com.event_booking_app.payment_service.dto.ProcessPaymentRequest;
import com.event_booking_app.payment_service.dto.RefundRequest;
import com.event_booking_app.payment_service.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/process")
    public ResponseEntity<PaymentResponse> processPayment(@Valid @RequestBody ProcessPaymentRequest request) {
        log.info("REST request to process payment for bookingId: {}", request.getBookingId());
        PaymentResponse response = paymentService.processPayment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPaymentById(@PathVariable UUID id) {
        log.info("REST request to get payment by ID: {}", id);
        PaymentResponse response = paymentService.getPaymentById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<PaymentResponse> getPaymentByBookingId(@PathVariable UUID bookingId) {
        log.info("REST request to get payment for bookingId: {}", bookingId);
        PaymentResponse response = paymentService.getPaymentByBookingId(bookingId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<PaymentResponse> refundPayment(
            @PathVariable UUID id,
            @RequestBody(required = false) RefundRequest request) {
        log.info("REST request to refund payment ID: {}", id);
        PaymentResponse response = paymentService.refundPayment(id, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/booking/{bookingId}/refund")
    public ResponseEntity<PaymentResponse> refundPaymentByBookingId(
            @PathVariable UUID bookingId,
            @RequestBody(required = false) RefundRequest request) {
        log.info("REST request to refund payment for bookingId: {}", bookingId);
        PaymentResponse response = paymentService.refundPaymentByBookingId(bookingId, request);
        return ResponseEntity.ok(response);
    }
}
