package com.shopnest.delivery;

import org.springframework.stereotype.Component;

/** The one place in our shop that talks to the courier. */
@Component
public class CourierClient {

    private final CourierApi courierApi;

    public CourierClient(CourierApi courierApi) {
        this.courierApi = courierApi;
    }

    // TODO 2: protect this method with the circuit breaker called "courier" (one annotation).
    //         Its numbers are in application.properties (Task 1).
    public int estimateDays(String pincode) {
        return courierApi.daysTo(pincode);
    }
}
