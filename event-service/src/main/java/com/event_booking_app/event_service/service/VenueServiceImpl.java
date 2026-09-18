package com.event_booking_app.event_service.service;

import com.event_booking_app.event_service.dto.CreateVenueRequest;
import com.event_booking_app.event_service.dto.VenueResponse;
import com.event_booking_app.event_service.entity.Venue;
import com.event_booking_app.event_service.exception.VenueNotFoundException;
import com.event_booking_app.event_service.mapper.VenueMapper;
import com.event_booking_app.event_service.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VenueServiceImpl implements VenueService {

    private final VenueRepository venueRepository;
    private final VenueMapper venueMapper;

    @Override
    @Transactional
    public VenueResponse createVenue(CreateVenueRequest request) {
        log.info("Creating new venue: {}", request.getName());
        Venue venue = venueMapper.toEntity(request);
        Venue savedVenue = venueRepository.save(venue);
        log.info("Venue created successfully with ID: {}", savedVenue.getId());
        return venueMapper.toResponse(savedVenue);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VenueResponse> getAllVenues() {
        log.debug("Fetching all venues");
        return venueRepository.findAll().stream()
                .map(venueMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public VenueResponse getVenueById(UUID id) {
        log.debug("Fetching venue by ID: {}", id);
        Venue venue = venueRepository.findById(id)
                .orElseThrow(() -> new VenueNotFoundException(id));
        return venueMapper.toResponse(venue);
    }
}
