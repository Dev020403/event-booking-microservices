package com.event_booking_app.ai_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Display history of a conversation.
 *
 * <p>Deliberately separate from Spring AI's chat memory: that store is a sliding window used as LLM
 * context and permanently drops older messages. This table keeps the full transcript for the UI.
 */
@Entity
@Table(name = "conversation_messages",
        indexes = @Index(name = "idx_conversation_messages_conversation_id", columnList = "conversation_id, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // required by JPA; not for application code
public class ConversationMessage {

    /** Identity column: monotonic, so it gives a stable ordering even within the same instant. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private MessageRole role;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ConversationMessage(UUID conversationId, MessageRole role, String content, Instant createdAt) {
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
    }
}
