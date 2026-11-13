package com.event_booking_app.ai_service.repository;

import com.event_booking_app.ai_service.entity.ConversationMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {

    List<ConversationMessage> findByConversationIdOrderByIdAsc(UUID conversationId);

    /** Bulk delete: one statement instead of loading every row just to remove it. */
    @Modifying
    @Query("delete from ConversationMessage m where m.conversationId = :conversationId")
    void deleteByConversationId(@Param("conversationId") UUID conversationId);
}
