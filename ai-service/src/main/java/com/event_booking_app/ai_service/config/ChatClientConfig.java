package com.event_booking_app.ai_service.config;

import com.event_booking_app.ai_service.tool.BookingTools;
import com.event_booking_app.ai_service.tool.DateTimeTools;
import com.event_booking_app.ai_service.tool.EventTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class ChatClientConfig {

    @Bean
    ChatMemory chatMemory(ChatMemoryRepository repository, AiProperties properties) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(properties.memoryWindowSize())
                .build();
    }

    @Bean
    ChatClient chatClient(ChatClient.Builder builder, AiProperties properties, ChatMemory chatMemory,
                          EventTools eventTools, DateTimeTools dateTimeTools, BookingTools bookingTools) {
        return builder
                .defaultSystem(properties.systemPrompt())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(eventTools, dateTimeTools, bookingTools)
                .build();
    }
}