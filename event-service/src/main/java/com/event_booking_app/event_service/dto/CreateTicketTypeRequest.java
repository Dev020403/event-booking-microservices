package com.event_booking_app.event_service.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTicketTypeRequest {

    @NotBlank(message = "Ticket type name is required")
    @Size(min = 2, max = 100, message = "Ticket type name must be between 2 and 100 characters")
    private String name;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Price cannot be negative")
    @DecimalMax(value = "99999999.99", message = "Price cannot exceed 99,999,999.99")
    @Digits(integer = 8, fraction = 2, message = "Price must have at most 8 integer digits and 2 decimal places")
    private BigDecimal price;

    /** ISO 4217 currency code (default: INR) */
    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a valid 3-letter uppercase ISO 4217 code")
    @Builder.Default
    private String currency = "INR";

    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    private String description;

    @NotNull(message = "Sales start date/time is required")
    private LocalDateTime salesStartAt;

    @NotNull(message = "Sales end date/time is required")
    private LocalDateTime salesEndAt;

    @AssertTrue(message = "Sales end date/time must be after sales start date/time")
    public boolean isSalesEndAfterStart() {
        if (salesStartAt == null || salesEndAt == null) {
            return true;
        }
        return salesEndAt.isAfter(salesStartAt);
    }
}
