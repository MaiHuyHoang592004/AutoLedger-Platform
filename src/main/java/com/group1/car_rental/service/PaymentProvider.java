package com.group1.car_rental.service;

import com.group1.car_rental.entity.Bookings;
import com.group1.car_rental.entity.Payments;

public interface PaymentProvider {

    /**
     * Authorize a payment for a booking
     * @param booking The booking to authorize payment for
     * @param providerRef External provider reference (e.g., transaction ID)
     * @param amountCents Amount in cents to authorize
     * @return Payment entity with authorization result
     */
    Payments authorize(Bookings booking, String providerRef, int amountCents);

    /**
     * Capture an authorized payment
     * @param booking The booking to capture payment for
     * @param authPayment The original authorization payment
     * @return Payment entity with capture result
     */
    Payments capture(Bookings booking, Payments authPayment);

    /**
     * Refund a captured payment
     * @param booking The booking to refund
     * @param originalPayment The original payment to refund
     * @param refundAmountCents Amount to refund in cents
     * @return Payment entity with refund result
     */
    Payments refund(Bookings booking, Payments originalPayment, int refundAmountCents);

    /**
     * Void an authorized payment (before capture)
     * @param booking The booking to void payment for
     * @param authPayment The authorization payment to void
     * @return Payment entity with void result
     */
    Payments voidAuthorization(Bookings booking, Payments authPayment);

    /**
     * Get the provider name (e.g., "stripe", "vnpay", "mock")
     */
    String getProviderName();
}
