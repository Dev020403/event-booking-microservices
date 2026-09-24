package com.event_booking_app.payment_service.strategy;

public record PaymentResult(
        boolean success,
        String providerTransactionId,
        String failureReason
) {
    public static PaymentResult success(String providerTransactionId) {
        return new PaymentResult(true, providerTransactionId, null);
    }

    public static PaymentResult failure(String reason) {
        return new PaymentResult(false, null, reason);
    }
}