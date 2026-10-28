package com.shopnest.delivery;

/**
 * The courier service: a "friend" that our shop calls. Already finished: do not change this file.
 * In a real shop this would be an HTTP call. Here it is a small interface, so that nothing needs the internet.
 */
public interface CourierApi {

    /**
     * How many days a parcel takes to this pincode.
     *
     * @throws CourierDownException when the courier has a bad moment
     */
    int daysTo(String pincode);
}
