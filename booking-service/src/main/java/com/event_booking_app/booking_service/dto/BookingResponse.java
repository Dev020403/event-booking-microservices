package com.event_booking_app.booking_service.dto;
import com.event_booking_app.booking_service.entity.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {
    private UUID id;
    private UUID userId;
    private UUID eventId;
    private UUID ticketTypeId;
    private UUID inventoryId;
    private UUID holdId;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private BookingStatus status;
    private UUID paymentId;
    private UUID idempotencyKey;
    private Instant createdAt;
    private Instant updatedAt;
}