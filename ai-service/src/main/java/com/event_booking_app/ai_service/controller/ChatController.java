package com.event_booking_app.ai_service.controller;

import com.event_booking_app.ai_service.dto.ChatRequest;
import com.event_booking_app.ai_service.dto.ChatResponse;
import com.event_booking_app.ai_service.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/v1/ai", produces = MediaType.APPLICATION_JSON_VALUE)
public class ChatController {
    private final ChatService chatService;
    @PostMapping(path = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return new ChatResponse(chatService.chat(request.message()));
    }
}
