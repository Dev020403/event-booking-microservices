package com.event_booking_app.ai_service.controller;

import com.event_booking_app.ai_service.config.AiProperties;
import com.event_booking_app.ai_service.dto.ChatRequest;
import com.event_booking_app.ai_service.dto.ChatResponse;
import com.event_booking_app.ai_service.dto.ChatStream;
import com.event_booking_app.ai_service.dto.StreamEvents;
import com.event_booking_app.ai_service.security.AuthenticatedUser;
import com.event_booking_app.ai_service.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/v1/ai", produces = MediaType.APPLICATION_JSON_VALUE)
public class ChatController {

    private final ChatService chatService;
    private final AiProperties properties;

    @PostMapping("/chat")
    public ChatResponse chat(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChatRequest request) {
        return chatService.chat(AuthenticatedUser.from(jwt), request);
    }

    @PostMapping(path = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> stream(@AuthenticationPrincipal Jwt jwt,
                                                @Valid @RequestBody ChatRequest request) {
        // Validation and ownership errors are thrown here, before streaming begins,
        // so they are returned as normal HTTP error responses.
        ChatStream chatStream = chatService.stream(AuthenticatedUser.from(jwt), request);

        Flux<ServerSentEvent<Object>> tokens = chatStream.tokens()
                .map(text -> event("token", new StreamEvents.Token(text)))
                .timeout(properties.streamIdleTimeout());

        return Flux.concat(
                        Mono.just(event("start", new StreamEvents.Start(chatStream.conversationId()))),
                        tokens,
                        Mono.just(event("done", new StreamEvents.Done())))
                .onErrorResume(ex -> {
                    log.error("Streaming failed (conversationId={})", chatStream.conversationId(), ex);
                    return Flux.just(event("error",
                            new StreamEvents.Error("The AI assistant could not complete the response.")));
                });
    }

    private static ServerSentEvent<Object> event(String name, Object data) {
        return ServerSentEvent.<Object>builder().event(name).data(data).build();
    }
}