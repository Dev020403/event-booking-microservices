package com.event_booking_app.ai_service.dto;

import java.util.List;

public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
}