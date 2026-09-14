package com.event_booking_app.user_service.controller;

import com.event_booking_app.user_service.dto.AuthResponse;
import com.event_booking_app.user_service.dto.LoginRequest;
import com.event_booking_app.user_service.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @RequestHeader("Authorization") String authHeader) {

        String refreshToken = authHeader.startsWith("Bearer ")
                ? authHeader.substring(7)
                : authHeader;

        return ResponseEntity.ok(authService.refresh(refreshToken));
    }
}
