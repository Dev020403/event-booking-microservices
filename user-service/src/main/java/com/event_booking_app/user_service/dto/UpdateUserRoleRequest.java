package com.event_booking_app.user_service.dto;

import com.event_booking_app.user_service.entity.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(
        @NotNull(message = "Role is required")
        Role role
) {}