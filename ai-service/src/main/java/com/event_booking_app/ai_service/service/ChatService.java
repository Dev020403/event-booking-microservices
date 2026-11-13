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
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
        conversationService.recordExchange(conversation.getId(), request.message(), reply);
        return new ChatResponse(conversation.getId(), reply);
    }

    public ChatStream stream(AuthenticatedUser user, ChatRequest request) {
        Conversation conversation =
                conversationService.resolve(user.id(), request.conversationId(), request.message());
        log.debug("Stream request (conversationId={}, length={})",
                conversation.getId(), request.message().length());

        // defer: every subscription gets its own buffer for the reply being streamed.
        Flux<String> tokens = Flux.defer(() -> {
            StringBuilder reply = new StringBuilder();
            return prompt(user, conversation.getId(), request.message()).stream().content()
                    .doOnNext(reply::append)
                    .concatWith(persistOnCompletion(conversation.getId(), request.message(), reply));
        });
        return new ChatStream(conversation.getId(), tokens);
    }

    private ChatClient.ChatClientRequestSpec prompt(AuthenticatedUser user, UUID conversationId, String message) {
        return chatClient.prompt()
                .user(message)
                .toolContext(ToolUserContext.asMap(user))
                .advisors(advisors -> advisors.param(ChatMemory.CONVERSATION_ID, conversationId.toString()));
    }

    /**
     * Runs after the last token. JDBC blocks, so it is moved off the reactive thread. A persistence
     * failure is logged but must not turn an already-delivered answer into a stream error.
     */
    private Mono<String> persistOnCompletion(UUID conversationId, String userMessage, StringBuilder reply) {
        return Mono.<String>fromRunnable(() -> {
                    if (!reply.isEmpty()) {
                        conversationService.recordExchange(conversationId, userMessage, reply.toString());
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(ex -> {
                    log.error("Failed to persist conversation history (conversationId={})", conversationId, ex);
                    return Mono.empty();
                });
    }
}
