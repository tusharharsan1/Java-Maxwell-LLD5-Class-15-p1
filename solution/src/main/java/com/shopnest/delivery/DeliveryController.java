package com.shopnest.delivery;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/delivery")
public class DeliveryController {

    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    // Task 4: GET /delivery/{pincode}  (the base route /delivery comes from the class)
    @GetMapping("/{pincode}")
    public DeliveryEstimate estimate(@PathVariable String pincode) {
        return deliveryService.estimate(pincode);
    }
}
