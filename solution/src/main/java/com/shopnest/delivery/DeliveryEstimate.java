package com.shopnest.delivery;

/**
 * What the shop tells the customer. Already finished: do not change this file.
 *
 * @param pincode the place we deliver to
 * @param days    how many days the parcel takes
 * @param source  "courier" when the courier gave the number, "default" when we used our own simple answer
 */
public record DeliveryEstimate(String pincode, int days, String source) {
}
