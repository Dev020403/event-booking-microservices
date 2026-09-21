package com.event_booking_app.inventory_service.repository;

import com.event_booking_app.inventory_service.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    boolean existsByEventIdAndTicketTypeId(UUID eventId, UUID ticketTypeId);

    Optional<Inventory> findByEventIdAndTicketTypeId(UUID eventId, UUID ticketTypeId);
}
