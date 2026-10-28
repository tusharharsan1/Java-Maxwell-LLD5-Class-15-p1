package com.shopnest.delivery;

import org.springframework.stereotype.Component;

/**
 * A pretend courier, for practice. It always says 3 days, unless you switch it off
 * (see PretendController). Each call writes one line in the console, so you can count
 * the calls that really reach the courier.
 * Already finished: do not change this file.
 */
@Component
public class PretendCourier implements CourierApi {

    private volatile boolean down = false;

    public void setDown(boolean down) {
        this.down = down;
    }

    @Override
    public int daysTo(String pincode) {
        if (down) {
            System.out.println("pretend courier: " + pincode + " -> I am down");
            throw new CourierDownException("the courier is down");
        }
        System.out.println("pretend courier: " + pincode + " -> 3 days");
        return 3;
    }
}
