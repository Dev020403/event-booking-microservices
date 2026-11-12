package com.event_booking_app.ai_service.controller;

import com.event_booking_app.ai_service.config.AiProperties;
import com.event_booking_app.ai_service.config.SecurityConfig;
import com.event_booking_app.ai_service.dto.ChatRequest;
import com.event_booking_app.ai_service.dto.ChatResponse;
import com.event_booking_app.ai_service.dto.ChatStream;
import com.event_booking_app.ai_service.exception.AiProviderException;
import com.event_booking_app.ai_service.exception.ConversationNotFoundException;
import com.event_booking_app.ai_service.security.AuthenticatedUser;
import com.event_booking_app.ai_service.service.ChatService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import reactor.core.publisher.Flux;

import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
@Import({SecurityConfig.class, ChatControllerTest.TestConfig.class})
@TestPropertySource(properties = "jwt.secret=" + ChatControllerTest.TEST_SECRET)
class ChatControllerTest {

    /** Base64 of a test-only key; never used outside tests. */
    static final String TEST_SECRET = "dGVzdC1vbmx5LWhzMjU2LXNlY3JldC1mb3ItYWktc2VydmljZS11bml0LXRlc3Rz";

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CONVERSATION_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    @TestConfiguration
    static class TestConfig {
        @Bean
        AiProperties aiProperties() {
            return new AiProperties("prompt", 20, Duration.ofSeconds(5), ZoneId.of("UTC"));
        }
    }

    @Test
    void chatReturnsReplyForAuthenticatedUser() throws Exception {
        when(chatService.chat(any(), any())).thenReturn(new ChatResponse(CONVERSATION_ID, "Hi there"));

        mockMvc.perform(post("/api/v1/ai/chat").with(user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(CONVERSATION_ID.toString()))
                .andExpect(jsonPath("$.reply").value("Hi there"));

        verify(chatService).chat(
                org.mockito.ArgumentMatchers.argThat((AuthenticatedUser u) -> u.id().equals(USER_ID)),
                eq(new ChatRequest(null, "Hello")));
    }

    @Test
    void chatAcceptsTokenSignedWithConfiguredSecret() throws Exception {
        when(chatService.chat(any(), any())).thenReturn(new ChatResponse(CONVERSATION_ID, "ok"));

        mockMvc.perform(post("/api/v1/ai/chat")
                        .header("Authorization", "Bearer " + signedToken(USER_ID.toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Hello\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void chatRejectsMissingToken() throws Exception {
        mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Hello\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(chatService);
    }

    @Test
    void chatRejectsTokenWithNonUuidSubject() throws Exception {
        mockMvc.perform(post("/api/v1/ai/chat").with(jwt().jwt(j -> j.subject("not-a-uuid")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Hello\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(chatService);
    }

    @Test
    void chatRejectsBlankMessage() throws Exception {
        mockMvc.perform(post("/api/v1/ai/chat").with(user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(chatService);
    }

    @Test
    void chatRejectsTooLongMessage() throws Exception {
        String body = "{\"message\":\"" + "a".repeat(ChatRequest.MAX_MESSAGE_LENGTH + 1) + "\"}";

        mockMvc.perform(post("/api/v1/ai/chat").with(user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(chatService);
    }

    @Test
    void chatMapsForeignConversationTo404() throws Exception {
        when(chatService.chat(any(), any())).thenThrow(new ConversationNotFoundException());

        mockMvc.perform(post("/api/v1/ai/chat").with(user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"" + UUID.randomUUID() + "\",\"message\":\"Hi\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Conversation not found"));
    }

    @Test
    void chatMapsProviderFailureTo503WithoutLeakingDetails() throws Exception {
        when(chatService.chat(any(), any()))
                .thenThrow(new AiProviderException("Groq 429: key sk-123 rate limited"));

        MvcResult result = mockMvc.perform(post("/api/v1/ai/chat").with(user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Hi\"}"))
                .andExpect(status().isServiceUnavailable())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("sk-123");
    }

    @Test
    void streamEmitsStartTokensAndDone() throws Exception {
        when(chatService.stream(any(), any()))
                .thenReturn(new ChatStream(CONVERSATION_ID, Flux.just("Hel", "lo")));

        String body = performStream();

        assertThat(body)
                .contains("event:start").contains(CONVERSATION_ID.toString())
                .contains("event:token").contains("\"text\":\"Hel\"").contains("\"text\":\"lo\"")
                .contains("event:done")
                .doesNotContain("event:error");
        assertThat(body.indexOf("event:start")).isLessThan(body.indexOf("event:token"));
        assertThat(body.lastIndexOf("event:token")).isLessThan(body.indexOf("event:done"));
    }

    @Test
    void streamEmitsErrorEventWhenProviderFailsMidStream() throws Exception {
        when(chatService.stream(any(), any())).thenReturn(new ChatStream(CONVERSATION_ID,
                Flux.concat(Flux.just("Hel"), Flux.error(new RuntimeException("boom")))));

        String body = performStream();

        assertThat(body).contains("event:start").contains("event:error")
                .contains("could not complete the response")
                .doesNotContain("event:done").doesNotContain("boom");
    }

    @Test
    void streamRejectsForeignConversationBeforeStreaming() throws Exception {
        when(chatService.stream(any(), any())).thenThrow(new ConversationNotFoundException());

        mockMvc.perform(post("/api/v1/ai/chat/stream").with(user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"conversationId\":\"" + UUID.randomUUID() + "\",\"message\":\"Hi\"}"))
                .andExpect(status().isNotFound());
    }

    private String performStream() throws Exception {
        MvcResult started = mockMvc.perform(post("/api/v1/ai/chat/stream").with(user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"message\":\"Hi\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        started.getAsyncResult(5_000);
        return mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static RequestPostProcessor user() {
        return jwt().jwt(j -> j.subject(USER_ID.toString()));
    }

    private static String signedToken(String subject) {
        var key = new SecretKeySpec(Base64.getDecoder().decode(TEST_SECRET), "HmacSHA256");
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        var claims = JwtClaimsSet.builder()
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
