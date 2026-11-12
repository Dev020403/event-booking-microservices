package com.event_booking_app.ai_service.support;

import feign.FeignException;
import feign.Request;
import feign.Response;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Builds real FeignExceptions (including status-specific subclasses such as NotFound) for tests. */
public final class FeignErrors {

    private FeignErrors() {
    }

    public static FeignException status(int status) {
        Request request = Request.create(Request.HttpMethod.GET, "http://test/api", Map.of(), null,
                StandardCharsets.UTF_8, null);
        Response response = Response.builder()
                .status(status)
                .reason("test")
                .request(request)
                .headers(Map.of())
                .body("{\"error\":\"test\"}", StandardCharsets.UTF_8)
                .build();
        return FeignException.errorStatus("TestClient#call()", response);
    }
}
