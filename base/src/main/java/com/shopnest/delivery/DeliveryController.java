package com.shopnest.delivery;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/delivery")
public class DeliveryController {

    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    // TODO 4: add the endpoint  GET /delivery/{pincode}  (the class already has the base route /delivery).
    //         It gives back the DeliveryEstimate that DeliveryService.estimate(pincode) makes.
}
