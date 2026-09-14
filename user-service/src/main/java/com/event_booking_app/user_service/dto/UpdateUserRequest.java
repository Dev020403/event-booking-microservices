package com.event_booking_app.user_service.dto;

import com.event_booking_app.user_service.entity.Role;
import jakarta.validation.constraints.*;

public record UpdateUserRequest(

        @Size(max = 100, message = "First name must not exceed 100 characters")
        String firstName,

        @Size(max = 100, message = "Last name must not exceed 100 characters")
        String lastName,

        @Email(message = "Email must be a valid address")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,

        Role role
) {}
