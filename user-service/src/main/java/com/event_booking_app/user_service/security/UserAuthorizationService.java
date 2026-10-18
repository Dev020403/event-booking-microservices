package com.event_booking_app.user_service.security;

import com.event_booking_app.user_service.dto.UpdateUserRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("userAuthorizationService")
@RequiredArgsConstructor
public class UserAuthorizationService {

    public boolean canAccessUser(UUID targetUserId) {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        // Admins can access any user's profile.
        if (isAdmin(authentication)) {
            return true;
        }

        // Other users can access only their own profile.
        return targetUserId.toString().equals(authentication.getName());
    }

    public boolean canUpdateUser(
            UUID targetUserId,
            UpdateUserRequest request
    ) {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        // Admins may update other users, including their roles.
        if (isAdmin(authentication)) {
            return true;
        }

        // Customers may update only themselves and cannot change roles.
        return targetUserId.toString().equals(authentication.getName())
                && request.role() == null;
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_ADMIN"));
    }
}