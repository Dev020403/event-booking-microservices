
package com.event_booking_app.event_service.repository;

import com.event_booking_app.event_service.entity.Event;
import com.event_booking_app.event_service.entity.EventCategory;
import com.event_booking_app.event_service.entity.EventStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EventSpecification {

    public static Specification<Event> withFilters(
            EventCategory category,
            String city,
            LocalDateTime fromDate,
            LocalDateTime toDate
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Public APIs must only return published events.
            predicates.add(
                    cb.equal(root.get("status"), EventStatus.PUBLISHED)
            );

            if (category != null) {
                predicates.add(
                        cb.equal(root.get("category"), category)
                );
            }

            if (city != null && !city.isBlank()) {
                predicates.add(
                        cb.equal(
                                cb.lower(root.get("venue").get("city")),
                                city.trim().toLowerCase()
                        )
                );
            }

            if (fromDate != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.get("startDateTime"), fromDate
                        )
                );
            }

            if (toDate != null) {
                predicates.add(
                        cb.lessThanOrEqualTo(
                                root.get("endDateTime"), toDate
                        )
                );
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Event> withManagementFilters(
            UUID organizerId,
            EventCategory category,
            EventStatus status,
            String city,
            LocalDateTime fromDate,
            LocalDateTime toDate
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // If organizerId is provided, restrict results to that organizer.
            // If null, this can be used by an admin to see all events.
            if (organizerId != null) {
                predicates.add(
                        cb.equal(root.get("organizerId"), organizerId)
                );
            }

            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }

            // Management endpoints may filter by any status, or show all statuses.
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (city != null && !city.isBlank()) {
                predicates.add(
                        cb.equal(
                                cb.lower(root.get("venue").get("city")),
                                city.trim().toLowerCase()
                        )
                );
            }

            if (fromDate != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.get("startDateTime"), fromDate
                        )
                );
            }

            if (toDate != null) {
                predicates.add(
                        cb.lessThanOrEqualTo(
                                root.get("endDateTime"), toDate
                        )
                );
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

}
