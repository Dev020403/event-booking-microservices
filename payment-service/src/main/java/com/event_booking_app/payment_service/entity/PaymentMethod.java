package com.event_booking_app.payment_service.entity;

import com.fasterxml.jackson.annotation.JsonAlias;

public enum PaymentMethod {
    // Booking Service sends CREDIT_CARD / DEBIT_CARD; both are charged through the
    // card strategy. Aliased at the JSON boundary (rather than adding enum values)
    // so the stored value and any existing enum CHECK constraint stay unchanged.
    @JsonAlias({"CREDIT_CARD", "DEBIT_CARD"})
    CARD,
    UPI,
    WALLET
}
