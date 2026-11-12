package com.event_booking_app.ai_service.client;

import com.event_booking_app.ai_service.client.dto.BookingPageResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "booking-service")
public interface BookingClient {

    @GetMapping("/api/v1/bookings/my")
    BookingPageResponse getMyBookings(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                      @RequestParam("page") int page,
                                      @RequestParam("size") int size,
                                      @RequestParam("sort") String sort);
}