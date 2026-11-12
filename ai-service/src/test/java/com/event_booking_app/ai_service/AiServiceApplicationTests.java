package com.event_booking_app.ai_service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Full-context smoke test. It needs real infrastructure (Postgres, JWT secret, Groq key), so it only
 * runs where those are configured; the unit and slice tests cover behaviour without them.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "JWT_SECRET", matches = ".+")
@EnabledIfEnvironmentVariable(named = "GROQ_API_KEY", matches = ".+")
class AiServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
