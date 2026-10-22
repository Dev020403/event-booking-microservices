package com.event_booking_app.notification_service.repository;

import com.event_booking_app.notification_service.entity.EmailStatus;
import com.event_booking_app.notification_service.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    boolean existsBySourceKey(String sourceKey);

    Page<Notification> findByUserId(UUID userId, Pageable pageable);

    Page<Notification> findByUserIdAndReadFalse(UUID userId, Pageable pageable);

    long countByUserIdAndReadFalse(UUID userId);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.read = true, n.readAt = :now WHERE n.userId = :userId AND n.read = false")
    int markAllRead(@Param("userId") UUID userId, @Param("now") Instant now);

    /**
     * Atomically claims a notification for sending. Only one caller (the Kafka listener
     * or a retry job on any instance) can win, so the same email is never sent twice
     * concurrently. Claimable: PENDING; FAILED once the retry interval has passed; and
     * SENDING only once it is stuck past the timeout.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.emailStatus = :sending, n.emailAttempts = n.emailAttempts + 1, "
            + "n.emailLastAttemptAt = :now "
            + "WHERE n.id = :id AND n.emailAttempts < :maxAttempts AND ("
            + "n.emailStatus = :pending "
            + "OR (n.emailStatus = :failed AND n.emailLastAttemptAt < :retryBefore) "
            + "OR (n.emailStatus = :sending AND n.emailLastAttemptAt < :stuckBefore))")
    int claimForSending(@Param("id") UUID id,
                        @Param("pending") EmailStatus pending,
                        @Param("failed") EmailStatus failed,
                        @Param("sending") EmailStatus sending,
                        @Param("maxAttempts") int maxAttempts,
                        @Param("now") Instant now,
                        @Param("retryBefore") Instant retryBefore,
                        @Param("stuckBefore") Instant stuckBefore);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.emailStatus = :status, n.emailSentAt = :sentAt, n.emailLastError = :error "
            + "WHERE n.id = :id")
    int recordSendResult(@Param("id") UUID id,
                         @Param("status") EmailStatus status,
                         @Param("sentAt") Instant sentAt,
                         @Param("error") String error);

    /**
     * Emails due for a (re)try: PENDING/FAILED not attempted within the retry interval,
     * plus SENDING ones stuck past the timeout (the sender likely crashed mid-send).
     */
    @Query("SELECT n.id FROM Notification n WHERE n.emailAttempts < :maxAttempts AND ("
            + "(n.emailStatus IN :retryable AND (n.emailLastAttemptAt IS NULL OR n.emailLastAttemptAt < :retryBefore)) "
            + "OR (n.emailStatus = :sending AND n.emailLastAttemptAt < :stuckBefore)) "
            + "ORDER BY n.createdAt")
    List<UUID> findIdsDueForEmail(@Param("retryable") Collection<EmailStatus> retryable,
                                  @Param("sending") EmailStatus sending,
                                  @Param("maxAttempts") int maxAttempts,
                                  @Param("retryBefore") Instant retryBefore,
                                  @Param("stuckBefore") Instant stuckBefore,
                                  Pageable pageable);
}
