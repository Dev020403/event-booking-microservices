package com.event_booking_app.user_service.service;

import com.event_booking_app.user_service.dto.CreateUserRequest;
import com.event_booking_app.user_service.dto.UpdateUserRequest;
import com.event_booking_app.user_service.dto.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface UserService {
    UserResponse createUser(CreateUserRequest request);

    UserResponse updateUser(UUID Id, UpdateUserRequest request);

    UserResponse getUserById(UUID Id);

    Page<UserResponse> getAllUsers(Pageable pageable);

    void deleteUser(UUID id);
}
