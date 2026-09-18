package com.event_booking_app.event_service.service;

import com.event_booking_app.event_service.dto.CreateVenueRequest;
import com.event_booking_app.event_service.dto.VenueResponse;

import java.util.List;
import java.util.UUID;

public interface VenueService {
    VenueResponse createVenue(CreateVenueRequest request);
    List<VenueResponse> getAllVenues();
    VenueResponse getVenueById(UUID id);
}
