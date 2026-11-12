package com.event_booking_app.ai_service.controller;

import com.event_booking_app.ai_service.dto.ConversationSummary;
import com.event_booking_app.ai_service.dto.MessageView;
import com.event_booking_app.ai_service.dto.PageResponse;
import com.event_booking_app.ai_service.security.AuthenticatedUser;
import com.event_booking_app.ai_service.service.ConversationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping
    public PageResponse<ConversationSummary> list(@AuthenticationPrincipal Jwt jwt,
                                                  @RequestParam(defaultValue = "0") @Min(0) int page,
                                                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return conversationService.list(AuthenticatedUser.from(jwt).id(), page, size);
    }

    @GetMapping("/{id}/messages")
    public List<MessageView> messages(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return conversationService.messages(AuthenticatedUser.from(jwt).id(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        conversationService.delete(AuthenticatedUser.from(jwt).id(), id);
    }
}