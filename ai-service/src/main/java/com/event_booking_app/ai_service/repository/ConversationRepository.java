package com.event_booking_app.ai_service.repository;

import com.event_booking_app.ai_service.entity.Conversation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, UUID>{
    Optional<Conversation> findByIdAndUserId(UUID id, UUID userId);

    Page<Conversation> findByUserIdOrderByUpdatedAtDesc(UUID userId, Pageable pageable);
}
