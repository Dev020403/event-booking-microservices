package com.event_booking_app.ai_service.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class ChatClientConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder, AiProperties properties) {
        return builder
                .defaultSystem(properties.systemPrompt())
                .build();
    }
}