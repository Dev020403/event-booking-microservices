package com.event_booking_app.event_service.repository;

import com.event_booking_app.event_service.entity.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface EventRepository extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {

    /**
     * Finds all events created by a specific organizer (useful for "my events" queries).
     */
    Page<Event> findByOrganizerId(UUID organizerId, Pageable pageable);
}
