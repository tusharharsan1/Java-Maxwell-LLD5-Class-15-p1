package com.shopnest.delivery;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The remote control of the pretend courier, for practice:
 *   curl "localhost:8080/pretend/courier?down=true"    switches the courier off
 *   curl "localhost:8080/pretend/courier?down=false"   switches it on again
 * Already finished: do not change this file.
 */
@RestController
public class PretendController {

    private final PretendCourier courier;

    public PretendController(PretendCourier courier) {
        this.courier = courier;
    }

    @GetMapping("/pretend/courier")
    public String set(@RequestParam(defaultValue = "false") boolean down) {
        courier.setDown(down);
        return "courier is now: down=" + down;
    }
}
