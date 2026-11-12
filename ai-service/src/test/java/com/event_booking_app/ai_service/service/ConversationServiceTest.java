package com.event_booking_app.ai_service.service;

import com.event_booking_app.ai_service.dto.ConversationSummary;
import com.event_booking_app.ai_service.dto.MessageView;
import com.event_booking_app.ai_service.dto.PageResponse;
import com.event_booking_app.ai_service.entity.Conversation;
import com.event_booking_app.ai_service.exception.ConversationNotFoundException;
import com.event_booking_app.ai_service.repository.ConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T08:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CONVERSATION_ID = UUID.randomUUID();

    @Mock
    private ConversationRepository repository;
    @Mock
    private ChatMemory chatMemory;

    private ConversationService service;

    @BeforeEach
    void setUp() {
        service = new ConversationService(Clock.fixed(NOW, ZoneOffset.UTC), repository, chatMemory);
    }

    @Test
    void resolveCreatesConversationWithNormalisedTitleWhenIdMissing() {
        when(repository.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));

        Conversation created = service.resolve(USER_ID, null, "  What   is on\n in Ahmedabad?  ");

        ArgumentCaptor<Conversation> saved = ArgumentCaptor.forClass(Conversation.class);
        verify(repository).save(saved.capture());
        assertThat(created).isSameAs(saved.getValue());
        assertThat(created.getUserId()).isEqualTo(USER_ID);
        assertThat(created.getTitle()).isEqualTo("What is on in Ahmedabad?");
        assertThat(created.getCreatedAt()).isEqualTo(NOW);
        assertThat(created.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void resolveTruncatesTitleTo60Chars() {
        when(repository.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));

        Conversation created = service.resolve(USER_ID, null, "a".repeat(100));

        assertThat(created.getTitle()).hasSize(60);
    }

    @Test
    void resolveTouchesExistingOwnedConversation() {
        Conversation existing = conversation(Instant.parse("2026-11-01T00:00:00Z"));
        when(repository.findByIdAndUserId(CONVERSATION_ID, USER_ID)).thenReturn(Optional.of(existing));

        Conversation resolved = service.resolve(USER_ID, CONVERSATION_ID, "next message");

        assertThat(resolved).isSameAs(existing);
        assertThat(resolved.getUpdatedAt()).isEqualTo(NOW);
        assertThat(resolved.getTitle()).isEqualTo("Original title");
        verify(repository, never()).save(any());
    }

    @Test
    void resolveRejectsConversationOwnedBySomeoneElse() {
        when(repository.findByIdAndUserId(CONVERSATION_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve(USER_ID, CONVERSATION_ID, "hi"))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void listMapsPageOfConversations() {
        Conversation c = conversation(NOW);
        PageRequest pageable = PageRequest.of(1, 2);
        when(repository.findByUserIdOrderByUpdatedAtDesc(USER_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(c), pageable, 3));

        PageResponse<ConversationSummary> page = service.list(USER_ID, 1, 2);

        assertThat(page.items()).containsExactly(
                new ConversationSummary(CONVERSATION_ID, "Original title", NOW, NOW));
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.totalItems()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(2);
    }

    @Test
    void messagesHidesSystemMessages() {
        when(repository.findByIdAndUserId(CONVERSATION_ID, USER_ID)).thenReturn(Optional.of(conversation(NOW)));
        when(chatMemory.get(CONVERSATION_ID.toString())).thenReturn(List.of(
                new SystemMessage("You are EventMate"),
                new UserMessage("Any conferences?"),
                new AssistantMessage("Yes, one in Ahmedabad.")));

        List<MessageView> messages = service.messages(USER_ID, CONVERSATION_ID);

        assertThat(messages).containsExactly(
                new MessageView("USER", "Any conferences?"),
                new MessageView("ASSISTANT", "Yes, one in Ahmedabad."));
    }

    @Test
    void messagesDoesNotReadMemoryForForeignConversation() {
        when(repository.findByIdAndUserId(CONVERSATION_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.messages(USER_ID, CONVERSATION_ID))
                .isInstanceOf(ConversationNotFoundException.class);
        verifyNoInteractions(chatMemory);
    }

    @Test
    void deleteRemovesConversationAndClearsMemory() {
        Conversation c = conversation(NOW);
        when(repository.findByIdAndUserId(CONVERSATION_ID, USER_ID)).thenReturn(Optional.of(c));

        service.delete(USER_ID, CONVERSATION_ID);

        verify(repository).delete(c);
        verify(chatMemory).clear(CONVERSATION_ID.toString());
    }

    @Test
    void deleteOfForeignConversationTouchesNothing() {
        when(repository.findByIdAndUserId(CONVERSATION_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(USER_ID, CONVERSATION_ID))
                .isInstanceOf(ConversationNotFoundException.class);
        verify(repository, never()).delete(any());
        verifyNoInteractions(chatMemory);
    }

    private static Conversation conversation(Instant createdAt) {
        Conversation c = new Conversation(USER_ID, "Original title", createdAt);
        ReflectionTestUtils.setField(c, "id", CONVERSATION_ID);
        return c;
    }
}
