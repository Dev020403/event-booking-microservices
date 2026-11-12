package com.event_booking_app.ai_service.service;

import com.event_booking_app.ai_service.dto.ChatRequest;
import com.event_booking_app.ai_service.dto.ChatStream;
import com.event_booking_app.ai_service.entity.Conversation;
import com.event_booking_app.ai_service.exception.AiProviderException;
import com.event_booking_app.ai_service.security.AuthenticatedUser;
import com.event_booking_app.ai_service.tool.ToolUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import com.event_booking_app.ai_service.dto.ChatResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {
    private final ChatClient chatClient;
    private final ConversationService conversationService;

    public ChatResponse chat(AuthenticatedUser user, ChatRequest request) {
        Conversation conversation =
                conversationService.resolve(user.id(), request.conversationId(), request.message());
        String reply;
        try {
            reply = prompt(user, conversation.getId(), request.message()).call().content();
        } catch (RuntimeException ex) {
            throw new AiProviderException("Chat request to AI provider failed", ex);
        }
        if (reply == null || reply.isBlank()) {
            throw new AiProviderException("AI provider returned an empty response");
        }
        return new ChatResponse(conversation.getId(), reply);
    }

    public ChatStream stream(AuthenticatedUser user, ChatRequest request) {
        Conversation conversation =
                conversationService.resolve(user.id(), request.conversationId(), request.message());
        log.debug("Stream request (conversationId={}, length={})",
                conversation.getId(), request.message().length());

        Flux<String> tokens = prompt(user, conversation.getId(), request.message()).stream().content();
        return new ChatStream(conversation.getId(), tokens);
    }

    private ChatClient.ChatClientRequestSpec prompt(AuthenticatedUser user, UUID conversationId, String message) {
        return chatClient.prompt()
                .user(message)
                .toolContext(ToolUserContext.asMap(user))
                .advisors(advisors -> advisors.param(ChatMemory.CONVERSATION_ID, conversationId.toString()));
    }
}
