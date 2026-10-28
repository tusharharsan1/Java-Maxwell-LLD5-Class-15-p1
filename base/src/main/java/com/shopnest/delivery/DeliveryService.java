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
        // TODO 3: today this line works only while the courier is healthy. Make the method safe:
        //   - when the courier gives a number, answer with source "courier" (as below);
        //   - when the call fails for any reason (the courier is down, or the breaker is OPEN and refuses),
        //     answer with DEFAULT_DAYS and the source "default". The customer must never see an error.
        return new DeliveryEstimate(pincode, courierClient.estimateDays(pincode), "courier");
    }
}
