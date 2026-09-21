package com.event_booking_app.inventory_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"event_id", "ticket_type_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;
    
    @Column(name = "ticket_type_id", nullable = false)
    private UUID ticketTypeId;
    
    @Column(nullable = false)
    private int totalQuantity;
    
    @Column(nullable = false)
    private int availableQuantity;

    @Column(nullable = false)
    private int heldQuantity;

    @Column(nullable = false)
    private int bookedQuantity;

    @Version
    private Long version;

    @Column(updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    protected void onCreate(){
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
