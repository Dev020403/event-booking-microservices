package com.event_booking_app.ai_service.tool;

import com.event_booking_app.ai_service.security.AuthenticatedUser;
import org.springframework.ai.chat.model.ToolContext;

import java.util.Map;

public final class ToolUserContext {
    private static final String KEY = "authenticatedUser";

    private ToolUserContext(){
    }

    public static Map<String, Object> asMap(AuthenticatedUser user){
        return Map.of(KEY, user);
    }

    /** Fails closed: a tool that needs a user must never run without one. */
    public static AuthenticatedUser require(ToolContext toolContext) {
        Object value = toolContext == null ? null : toolContext.getContext().get(KEY);
        if (value instanceof AuthenticatedUser user) {
            return user;
        }
        throw new IllegalStateException("No authenticated user in tool context");
    }
}
