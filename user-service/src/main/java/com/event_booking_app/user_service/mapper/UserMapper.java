package com.event_booking_app.user_service.mapper;

import com.event_booking_app.user_service.dto.CreateUserRequest;
import com.event_booking_app.user_service.dto.UpdateUserRequest;
import com.event_booking_app.user_service.dto.UserResponse;
import com.event_booking_app.user_service.entity.Role;
import com.event_booking_app.user_service.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    public User toEntity(CreateUserRequest request, String passwordHash) {
        Role role = request.role() != null ? request.role() : Role.CUSTOMER;

        return User.builder()
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .email(request.email().toLowerCase().trim())
                .passwordHash(passwordHash)
                .role(role)
                .build();
    }
    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
    public void applyUpdate(UpdateUserRequest request, User user) {

        String newFirstName = (request.firstName() != null)
                ? request.firstName().trim()
                : user.getFirstName();

        String newLastName = (request.lastName() != null)
                ? request.lastName().trim()
                : user.getLastName();

        if (!newFirstName.equals(user.getFirstName()) || !newLastName.equals(user.getLastName())) {
            user.updateName(newFirstName, newLastName);
        }

        if (request.email() != null) {
            String normalizedEmail = request.email().toLowerCase().trim();
            if (!normalizedEmail.equals(user.getEmail())) {
                user.updateEmail(normalizedEmail);
            }
        }

        if (request.role() != null && request.role() != user.getRole()) {
            user.updateRole(request.role());
        }
    }
}
