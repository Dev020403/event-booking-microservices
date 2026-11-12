package com.event_booking_app.ai_service.service;

import com.event_booking_app.ai_service.dto.ChatRequest;
import com.event_booking_app.ai_service.dto.ChatResponse;
import com.event_booking_app.ai_service.dto.ChatStream;
import com.event_booking_app.ai_service.entity.Conversation;
import com.event_booking_app.ai_service.exception.AiProviderException;
import com.event_booking_app.ai_service.exception.ConversationNotFoundException;
import com.event_booking_app.ai_service.security.AuthenticatedUser;
import com.event_booking_app.ai_service.tool.ToolUserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // fluent-spec stubs are shared; not every test uses each one
class ChatServiceTest {

    private static final AuthenticatedUser USER = new AuthenticatedUser(UUID.randomUUID(), "token");
    private static final UUID CONVERSATION_ID = UUID.randomUUID();

    @Mock
    private ChatClient chatClient;
    @Mock
    private ChatClient.ChatClientRequestSpec spec;
    @Mock
    private ChatClient.CallResponseSpec callSpec;
    @Mock
    private ChatClient.StreamResponseSpec streamSpec;
    @Mock
    private ChatClient.AdvisorSpec advisorSpec;
    @Mock
    private ConversationService conversationService;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(chatClient, conversationService);
        when(chatClient.prompt()).thenReturn(spec);
        when(spec.user(anyString())).thenReturn(spec);
        when(spec.toolContext(anyMap())).thenReturn(spec);
        when(spec.advisors(any(Consumer.class))).thenReturn(spec);
        when(spec.call()).thenReturn(callSpec);
        when(spec.stream()).thenReturn(streamSpec);
        when(conversationService.resolve(any(), any(), anyString())).thenReturn(conversation());
    }

    @Test
    void chatReturnsReplyAndPassesUserMessageToolContextAndConversationId() {
        when(callSpec.content()).thenReturn("Here are the tickets.");

        ChatResponse response = chatService.chat(USER, new ChatRequest(null, "What tickets?"));

        assertThat(response).isEqualTo(new ChatResponse(CONVERSATION_ID, "Here are the tickets."));
        verify(conversationService).resolve(USER.id(), null, "What tickets?");
        verify(spec).user("What tickets?");
        verify(spec).toolContext(Map.of("authenticatedUser", USER));
        assertConversationIdAdvisorParam();
    }

    @Test
    void chatWrapsProviderFailure() {
        when(callSpec.content()).thenThrow(new RuntimeException("429 rate limited"));

        assertThatThrownBy(() -> chatService.chat(USER, new ChatRequest(null, "hi")))
                .isInstanceOf(AiProviderException.class)
                .hasRootCauseMessage("429 rate limited");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void chatRejectsEmptyReply(String reply) {
        when(callSpec.content()).thenReturn(reply);

        assertThatThrownBy(() -> chatService.chat(USER, new ChatRequest(null, "hi")))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void chatDoesNotCallModelWhenConversationIsNotOwned() {
        UUID foreign = UUID.randomUUID();
        when(conversationService.resolve(USER.id(), foreign, "hi")).thenThrow(new ConversationNotFoundException());

        assertThatThrownBy(() -> chatService.chat(USER, new ChatRequest(foreign, "hi")))
                .isInstanceOf(ConversationNotFoundException.class);
        verifyNoInteractions(chatClient);
    }

    @Test
    void streamReturnsConversationIdAndTokens() {
        when(streamSpec.content()).thenReturn(Flux.just("Hel", "lo"));

        ChatStream stream = chatService.stream(USER, new ChatRequest(null, "hi"));

        assertThat(stream.conversationId()).isEqualTo(CONVERSATION_ID);
        assertThat(stream.tokens().collectList().block()).containsExactly("Hel", "lo");
        verify(spec).toolContext(ToolUserContext.asMap(USER));
        assertConversationIdAdvisorParam();
    }

    @SuppressWarnings("unchecked")
    private void assertConversationIdAdvisorParam() {
        ArgumentCaptor<Consumer<ChatClient.AdvisorSpec>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(spec).advisors(captor.capture());
        captor.getValue().accept(advisorSpec);
        verify(advisorSpec).param(ChatMemory.CONVERSATION_ID, CONVERSATION_ID.toString());
    }

    private static Conversation conversation() {
        Conversation c = new Conversation(USER.id(), "title", Instant.now());
        ReflectionTestUtils.setField(c, "id", CONVERSATION_ID);
        return c;
    }
}
