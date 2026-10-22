package com.event_booking_app.notification_service.service;

import com.event_booking_app.notification_service.entity.NotificationType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BookingNotificationFactoryTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final BookingNotificationFactory factory = new BookingNotificationFactory();

    private final UUID bookingId = UUID.fromString("3f2a9c1e-0000-4000-8000-000000000001");
    private final UUID userId = UUID.randomUUID();

    private ObjectNode event(String type) {
        ObjectNode node = mapper.createObjectNode();
        node.put("eventType", type);
        node.put("bookingId", bookingId.toString());
        node.put("userId", userId.toString());
        node.put("userEmail", "user@example.com");
        node.put("quantity", 2);
        return node;
    }

    @ParameterizedTest
    @EnumSource(NotificationType.class)
    void everyNotificationType_producesDraft(NotificationType type) {
        NotificationDraft draft = factory.fromBookingEvent(event(type.name())).orElseThrow();

        assertEquals(type, draft.type());
        assertEquals(userId, draft.userId());
        assertEquals(bookingId, draft.bookingId());
        assertEquals("user@example.com", draft.recipientEmail());
        assertTrue(draft.title().contains("#3F2A9C1E"), draft.title());
        assertTrue(draft.message().contains("2 tickets"), draft.message());
        assertEquals(type.name() + ":" + bookingId, draft.sourceKey());
    }

    @Test
    void confirmed_includesFormattedAmountAndCurrency() {
        ObjectNode node = event("BOOKING_CONFIRMED");
        node.put("totalAmount", new BigDecimal("1500.5"));
        node.put("currency", "INR");

        NotificationDraft draft = factory.fromBookingEvent(node).orElseThrow();

        assertTrue(draft.message().contains("Amount paid: 1500.50 INR."), draft.message());
    }

    @Test
    void cancelled_mentionsRefundOnlyWhenRefunded() {
        ObjectNode refunded = event("BOOKING_CANCELLED");
        refunded.put("refunded", true);
        ObjectNode notRefunded = event("BOOKING_CANCELLED");

        assertTrue(factory.fromBookingEvent(refunded).orElseThrow().message().contains("refund"));
        assertFalse(factory.fromBookingEvent(notRefunded).orElseThrow().message().contains("refund"));
    }

    @Test
    void paymentFailed_doesNotLeakInternalReason() {
        ObjectNode node = event("BOOKING_PAYMENT_FAILED");
        node.put("reason", "feign.RetryableException: Connection refused executing POST http://payment-service");

        NotificationDraft draft = factory.fromBookingEvent(node).orElseThrow();

        assertFalse(draft.message().contains("feign"));
    }

    @Test
    void singleTicket_usesSingular() {
        ObjectNode node = event("BOOKING_EXPIRED");
        node.put("quantity", 1);

        assertTrue(factory.fromBookingEvent(node).orElseThrow().message().contains("1 ticket "));
    }

    @Test
    void blankOrMissingEmail_meansInAppOnly() {
        ObjectNode blank = event("BOOKING_CREATED");
        blank.put("userEmail", "  ");
        ObjectNode missing = event("BOOKING_CREATED");
        missing.remove("userEmail");

        assertNull(factory.fromBookingEvent(blank).orElseThrow().recipientEmail());
        assertNull(factory.fromBookingEvent(missing).orElseThrow().recipientEmail());
    }

    @Test
    void unknownOrMissingEventType_isIgnored() {
        assertEquals(Optional.empty(), factory.fromBookingEvent(event("BOOKING_SOMETHING_NEW")));

        ObjectNode untyped = event("BOOKING_CREATED");
        untyped.remove("eventType");
        assertEquals(Optional.empty(), factory.fromBookingEvent(untyped));
    }

    @Test
    void missingBookingId_isRejected() {
        ObjectNode node = event("BOOKING_CREATED");
        node.remove("bookingId");

        assertThrows(IllegalArgumentException.class, () -> factory.fromBookingEvent(node));
    }
}
