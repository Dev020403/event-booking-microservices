package com.event_booking_app.payment_service.service;

import com.event_booking_app.payment_service.dto.PaymentResponse;
import com.event_booking_app.payment_service.dto.ProcessPaymentRequest;
import com.event_booking_app.payment_service.dto.RefundRequest;

import java.util.UUID;

public interface PaymentService {

    PaymentResponse processPayment(ProcessPaymentRequest request);

    PaymentResponse getPaymentById(UUID id);

    PaymentResponse getPaymentByBookingId(UUID bookingId);

    PaymentResponse refundPayment(UUID id, RefundRequest request);

    PaymentResponse refundPaymentByBookingId(UUID bookingId, RefundRequest request);
}
