package com.shopnest.delivery;

import org.springframework.stereotype.Service;

@Service
public class DeliveryService {

    /** Our own simple answer, for when the courier cannot help: 5 days. */
    static final int DEFAULT_DAYS = 5;

    private final CourierClient courierClient;

    public DeliveryService(CourierClient courierClient) {
        this.courierClient = courierClient;
    }

    public DeliveryEstimate estimate(String pincode) {
        // Task 3: a RuntimeException covers both a courier that is down (CourierDownException)
        // and a breaker that is OPEN and refuses the call (CallNotPermittedException).
        try {
            return new DeliveryEstimate(pincode, courierClient.estimateDays(pincode), "courier");
        } catch (RuntimeException e) {
            return new DeliveryEstimate(pincode, DEFAULT_DAYS, "default");
        }
    }
}
