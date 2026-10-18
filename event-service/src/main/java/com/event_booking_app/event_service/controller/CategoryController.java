package com.event_booking_app.event_service.controller;

import com.event_booking_app.event_service.dto.CategoryResponse;
import com.event_booking_app.event_service.entity.EventCategory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getCategories() {
        List<CategoryResponse> categories = Arrays.stream(EventCategory.values())
                .map(cat -> CategoryResponse.of(
                        cat.name(),
                        cat.name().charAt(0) + cat.name().substring(1).toLowerCase()
                ))
                .toList();
        return ResponseEntity.ok(categories);
    }
}
