package com.event_booking_app.ai_service.tool;

import com.event_booking_app.ai_service.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolUserContextTest {

    @Test
    void roundTripsAuthenticatedUser() {
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "token");

        assertThat(ToolUserContext.require(new ToolContext(ToolUserContext.asMap(user)))).isSameAs(user);
    }

    @Test
    void failsClosedForNullContext() {
        assertThatThrownBy(() -> ToolUserContext.require(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsClosedWhenUserMissing() {
        assertThatThrownBy(() -> ToolUserContext.require(new ToolContext(Map.of())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsClosedWhenValueHasWrongType() {
        // e.g. a model-supplied or forged value must not be accepted as the user.
        ToolContext forged = new ToolContext(Map.of("authenticatedUser", UUID.randomUUID().toString()));

        assertThatThrownBy(() -> ToolUserContext.require(forged)).isInstanceOf(IllegalStateException.class);
    }
}
