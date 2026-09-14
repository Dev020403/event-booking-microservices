package com.event_booking_app.user_service.service;

import com.event_booking_app.user_service.dto.AuthResponse;
import com.event_booking_app.user_service.dto.LoginRequest;

public interface AuthService {


    AuthResponse login(LoginRequest request);

    AuthResponse refresh(String refreshToken);
}
