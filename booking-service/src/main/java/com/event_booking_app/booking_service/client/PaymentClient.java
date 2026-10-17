package com.event_booking_app.booking_service.client;

import com.event_booking_app.booking_service.client.dto.PaymentResponse;
import com.event_booking_app.booking_service.client.dto.ProcessPaymentRequest;
import com.event_booking_app.booking_service.client.dto.RefundRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "payment-service", configuration = FeignClientInterceptor.class)
public interface PaymentClient {

    @PostMapping("/api/v1/payments/process")
    PaymentResponse processPayment(@RequestBody ProcessPaymentRequest request);

    @GetMapping("/api/v1/payments/{id}")
    PaymentResponse getPaymentById(@PathVariable("id") UUID id);

    @GetMapping("/api/v1/payments/booking/{bookingId}")
    PaymentResponse getPaymentByBookingId(@PathVariable("bookingId") UUID bookingId);

    @PostMapping("/api/v1/payments/{id}/refund")
    PaymentResponse refundPayment(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) RefundRequest request
    );

    @PostMapping("/api/v1/payments/booking/{bookingId}/refund")
    PaymentResponse refundPaymentByBookingId(
            @PathVariable("bookingId") UUID bookingId,
            @RequestBody(required = false) RefundRequest request
    );
}
