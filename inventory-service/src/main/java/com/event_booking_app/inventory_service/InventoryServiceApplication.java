package com.event_booking_app.inventory_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableRetry's advice is ordered outside @Transactional by default, so each
// retry runs in a fresh transaction and also catches optimistic-lock failures
// raised at commit time.
@SpringBootApplication
@EnableScheduling
@EnableRetry
public class InventoryServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(InventoryServiceApplication.class, args);
	}

}
