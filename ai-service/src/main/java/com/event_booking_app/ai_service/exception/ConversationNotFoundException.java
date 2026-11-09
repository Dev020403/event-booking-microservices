package com.event_booking_app.ai_service.exception;

public class ConversationNotFoundException extends RuntimeException {

    public ConversationNotFoundException() {
        super("Conversation not found");
    }
}