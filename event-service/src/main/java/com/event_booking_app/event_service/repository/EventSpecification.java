package com.event_booking_app.event_service.repository;

import com.event_booking_app.event_service.entity.Event;
import com.event_booking_app.event_service.entity.EventCategory;
import com.event_booking_app.event_service.entity.EventStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class EventSpecification {

    public static Specification<Event> withFilters(
            EventCategory category,
            EventStatus status,
            String city,
            LocalDateTime fromDate,
            LocalDateTime toDate
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (city != null && !city.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("venue").get("city")), city.trim().toLowerCase()));
            }

            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startDateTime"), fromDate));
            }

            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("endDateTime"), toDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
