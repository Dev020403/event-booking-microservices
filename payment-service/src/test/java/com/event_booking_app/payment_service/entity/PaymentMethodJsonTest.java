package com.event_booking_app.payment_service.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentMethodJsonTest {

    // Spring MVC in Boot 4 uses Jackson 3; Jackson 2 is still used by the security handlers.
    private final JsonMapper jackson3 = JsonMapper.builder().build();
    private final ObjectMapper jackson2 = new ObjectMapper();

    // Booking Service sends CREDIT_CARD / DEBIT_CARD; both must map onto CARD.
    @ParameterizedTest
    @CsvSource({"CARD,CARD", "CREDIT_CARD,CARD", "DEBIT_CARD,CARD", "UPI,UPI", "WALLET,WALLET"})
    void deserializesBookingServiceValues(String json, PaymentMethod expected) throws Exception {
        String quoted = "\"" + json + "\"";
        assertEquals(expected, jackson3.readValue(quoted, PaymentMethod.class));
        assertEquals(expected, jackson2.readValue(quoted, PaymentMethod.class));
    }
}
