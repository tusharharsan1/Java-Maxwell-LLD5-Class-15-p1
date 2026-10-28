package com.shopnest.delivery;

/** The courier did not give an answer. Already finished: do not change this file. */
public class CourierDownException extends RuntimeException {

    public CourierDownException(String message) {
        super(message);
    }
}
