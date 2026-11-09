package com.event_booking_app.ai_service.service;

import com.event_booking_app.ai_service.dto.ConversationSummary;
import com.event_booking_app.ai_service.dto.MessageView;
import com.event_booking_app.ai_service.dto.PageResponse;
import com.event_booking_app.ai_service.entity.Conversation;
import com.event_booking_app.ai_service.exception.ConversationNotFoundException;
import com.event_booking_app.ai_service.repository.ConversationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private static final int MAX_TITLE_LENGTH = 60;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final Clock clock;
    private final ConversationRepository conversationRepository;
    private final ChatMemory chatMemory;

    @Transactional
    public Conversation resolve(UUID userId, UUID conversationId, String firstMessage) {
        Instant now = clock.instant();
        if (conversationId == null) {
            return conversationRepository.save(new Conversation(userId, createTitle(firstMessage), now));
        }
        Conversation conversation = getOwned(userId, conversationId);
        conversation.touch(now);
        return conversation;
    }

    @Transactional(readOnly = true)
    public PageResponse<ConversationSummary> list(UUID userId, int page, int size) {
        Page<Conversation> result =
                conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId, PageRequest.of(page, size));
        List<ConversationSummary> items = result.getContent().stream()
                .map(c -> new ConversationSummary(c.getId(), c.getTitle(), c.getCreatedAt(), c.getUpdatedAt()))
                .toList();
        return new PageResponse<>(items, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public List<MessageView> messages(UUID userId, UUID conversationId) {
        getOwned(userId, conversationId);
        return chatMemory.get(conversationId.toString()).stream()
                .filter(message -> message.getMessageType() != MessageType.SYSTEM)
                .map(message -> new MessageView(message.getMessageType().name(), message.getText()))
                .toList();
    }

    @Transactional
    public void delete(UUID userId, UUID conversationId) {
        Conversation conversation = getOwned(userId, conversationId);
        conversationRepository.delete(conversation);
        chatMemory.clear(conversationId.toString());
    }

    private Conversation getOwned(UUID userId, UUID conversationId) {
        return conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(ConversationNotFoundException::new);
    }

    private static String createTitle(String message) {
        String normalized = WHITESPACE.matcher(message.strip()).replaceAll(" ");
        return normalized.length() <= MAX_TITLE_LENGTH
                ? normalized
                : normalized.substring(0, MAX_TITLE_LENGTH).strip();
    }
}