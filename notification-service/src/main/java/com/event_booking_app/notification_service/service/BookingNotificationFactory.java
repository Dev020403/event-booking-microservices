package com.event_booking_app.notification_service.service;

import com.event_booking_app.notification_service.entity.NotificationType;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns a booking-service event (raw JSON) into user-facing notification content.
 * Unknown event types are ignored so booking-service can add events without
 * breaking this consumer.
 */
@Slf4j
@Component
public class BookingNotificationFactory {

    public Optional<NotificationDraft> fromBookingEvent(JsonNode event) {
        String eventType = text(event, "eventType");
        if (eventType == null) {
            return Optional.empty();
        }

        NotificationType type;
        try {
            type = NotificationType.valueOf(eventType);
        } catch (IllegalArgumentException ex) {
            log.debug("Ignoring booking event with unhandled type {}", eventType);
            return Optional.empty();
        }

        UUID bookingId = uuid(event, "bookingId");
        UUID userId = uuid(event, "userId");
        if (bookingId == null || userId == null) {
            throw new IllegalArgumentException(eventType + " event is missing bookingId or userId");
        }

        String ref = shortRef(bookingId);
        int quantity = event.path("quantity").asInt(0);
        String tickets = quantity + (quantity == 1 ? " ticket" : " tickets");

        String title;
        String message;
        switch (type) {
            case BOOKING_CREATED -> {
                title = "Booking " + ref + " reserved";
                message = "We're holding " + tickets + " for you. Complete payment soon — "
                        + "unpaid reservations are released automatically when the hold expires.";
            }
            case BOOKING_CONFIRMED -> {
                title = "Booking " + ref + " confirmed";
                message = "Your booking for " + tickets + " is confirmed."
                        + amountSentence(event, "Amount paid");
            }
            case BOOKING_CANCELLED -> {
                title = "Booking " + ref + " cancelled";
                message = "Your booking for " + tickets + " has been cancelled."
                        + (event.path("refunded").asBoolean(false)
                        ? " A refund has been issued to your original payment method." : "");
            }
            case BOOKING_PAYMENT_FAILED -> {
                // The raw failure reason can contain internal error text, so it is not shown.
                title = "Payment failed for booking " + ref;
                message = "We couldn't complete payment for " + tickets + ", so the seats have been "
                        + "released. Please try booking again.";
            }
            case BOOKING_EXPIRED -> {
                title = "Booking " + ref + " expired";
                message = "Your reservation for " + tickets + " expired before payment was completed, "
                        + "and the seats have been released.";
            }
            default -> throw new IllegalStateException("Unhandled notification type " + type);
        }

        return Optional.of(new NotificationDraft(userId, bookingId, type, title, message, blankToNull(text(event, "userEmail"))));
    }

    private static String amountSentence(JsonNode event, String label) {
        JsonNode amount = event.get("totalAmount");
        if (amount == null || amount.isNull()) {
            return "";
        }
        BigDecimal value = amount.decimalValue().setScale(2, RoundingMode.HALF_UP);
        String currency = text(event, "currency");
        return " " + label + ": " + value.toPlainString() + (currency != null ? " " + currency : "") + ".";
    }

    // First 8 hex chars of the booking id — enough for a user to recognise it
    private static String shortRef(UUID bookingId) {
        return "#" + bookingId.toString().substring(0, 8).toUpperCase();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asText();
    }

    private static UUID uuid(JsonNode node, String field) {
        String value = text(node, field);
        return value == null ? null : UUID.fromString(value);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
