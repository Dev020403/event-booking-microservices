package com.event_booking_app.ai_service.security;

import com.event_booking_app.ai_service.exception.InvalidTokenException;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticatedUserTest {

    @Test
    void readsUserIdFromSubjectAndKeepsToken() {
        UUID id = UUID.randomUUID();

        AuthenticatedUser user = AuthenticatedUser.from(jwt(id.toString()));

        assertThat(user.id()).isEqualTo(id);
        assertThat(user.accessToken()).isEqualTo("raw-token");
        assertThat(user.bearerToken()).isEqualTo("Bearer raw-token");
    }

    @Test
    void rejectsMissingSubject() {
        assertThatThrownBy(() -> AuthenticatedUser.from(jwt(null)))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void rejectsNonUuidSubject() {
        assertThatThrownBy(() -> AuthenticatedUser.from(jwt("someone@example.com")))
                .isInstanceOf(InvalidTokenException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toStringNeverContainsToken() {
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "super-secret-token");

        assertThat(user.toString()).doesNotContain("super-secret-token").contains("***");
    }

    private static Jwt jwt(String subject) {
        Jwt.Builder builder = Jwt.withTokenValue("raw-token")
                .header("alg", "HS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("scope", "user");
        if (subject != null) {
            builder.subject(subject);
        }
        return builder.build();
    }
}
