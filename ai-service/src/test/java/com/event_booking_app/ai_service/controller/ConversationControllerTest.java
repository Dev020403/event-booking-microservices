package com.event_booking_app.ai_service.controller;

import com.event_booking_app.ai_service.config.SecurityConfig;
import com.event_booking_app.ai_service.dto.ConversationSummary;
import com.event_booking_app.ai_service.dto.MessageView;
import com.event_booking_app.ai_service.dto.PageResponse;
import com.event_booking_app.ai_service.exception.ConversationNotFoundException;
import com.event_booking_app.ai_service.service.ConversationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConversationController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "jwt.secret=" + ChatControllerTest.TEST_SECRET)
class ConversationControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CONVERSATION_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConversationService conversationService;

    @Test
    void listUsesDefaultPaging() throws Exception {
        Instant now = Instant.parse("2026-11-12T08:00:00Z");
        when(conversationService.list(USER_ID, 0, 20)).thenReturn(new PageResponse<>(
                List.of(new ConversationSummary(CONVERSATION_ID, "Conferences", now, now)), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/ai/conversations").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(CONVERSATION_ID.toString()))
                .andExpect(jsonPath("$.items[0].title").value("Conferences"))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void listRejectsOversizedPage() throws Exception {
        mockMvc.perform(get("/api/v1/ai/conversations").param("size", "101").with(user()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(conversationService);
    }

    @Test
    void listRejectsNegativePage() throws Exception {
        mockMvc.perform(get("/api/v1/ai/conversations").param("page", "-1").with(user()))
                .andExpect(status().isBadRequest());
        verify(conversationService, org.mockito.Mockito.never()).list(any(), anyInt(), anyInt());
    }

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/ai/conversations")).andExpect(status().isUnauthorized());
    }

    @Test
    void messagesReturnsHistory() throws Exception {
        when(conversationService.messages(USER_ID, CONVERSATION_ID)).thenReturn(List.of(
                new MessageView("USER", "Any conferences?"),
                new MessageView("ASSISTANT", "One in Ahmedabad.")));

        mockMvc.perform(get("/api/v1/ai/conversations/{id}/messages", CONVERSATION_ID).with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("USER"))
                .andExpect(jsonPath("$[1].content").value("One in Ahmedabad."));
    }

    @Test
    void messagesOfForeignConversationIs404() throws Exception {
        when(conversationService.messages(USER_ID, CONVERSATION_ID)).thenThrow(new ConversationNotFoundException());

        mockMvc.perform(get("/api/v1/ai/conversations/{id}/messages", CONVERSATION_ID).with(user()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/ai/conversations/{id}", CONVERSATION_ID).with(user()))
                .andExpect(status().isNoContent());
        verify(conversationService).delete(USER_ID, CONVERSATION_ID);
    }

    @Test
    void deleteOfForeignConversationIs404() throws Exception {
        doThrow(new ConversationNotFoundException()).when(conversationService).delete(USER_ID, CONVERSATION_ID);

        mockMvc.perform(delete("/api/v1/ai/conversations/{id}", CONVERSATION_ID).with(user()))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidPathIdIs400() throws Exception {
        mockMvc.perform(get("/api/v1/ai/conversations/{id}/messages", "not-a-uuid").with(user()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(conversationService);
    }

    private static RequestPostProcessor user() {
        return jwt().jwt(j -> j.subject(USER_ID.toString()));
    }
}
