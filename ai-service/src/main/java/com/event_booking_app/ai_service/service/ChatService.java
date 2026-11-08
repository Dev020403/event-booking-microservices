package com.event_booking_app.ai_service.service;

import com.event_booking_app.ai_service.exception.AiProviderException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {
    private final ChatClient chatClient;
    public String chat(String message){
        log.debug("Sending chat request to model (length={})", message.length());
        String reply;
        try {
            reply = chatClient.prompt()
                    .user(message)
                    .call()
                    .content();
        }catch (Exception ex){
            throw new AiProviderException("Chat request to AI provider failed", ex);
        }
        if (reply == null || reply.isBlank()) {
            throw new AiProviderException("AI provider returned an empty response");
        }
        return reply;
    }
}
