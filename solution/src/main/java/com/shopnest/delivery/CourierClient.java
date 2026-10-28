package com.shopnest.delivery;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Component;

/** The one place in our shop that talks to the courier. */
@Component
public class CourierClient {

    private final CourierApi courierApi;

    public CourierClient(CourierApi courierApi) {
        this.courierApi = courierApi;
    }

    // Task 2: the circuit breaker called "courier" (its numbers are in application.properties).
    // When the breaker is OPEN, Spring stops the call before it starts, and throws an exception.
    @CircuitBreaker(name = "courier")
    public int estimateDays(String pincode) {
        return courierApi.daysTo(pincode);
    }
}
