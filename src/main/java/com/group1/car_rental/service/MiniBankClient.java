package com.group1.car_rental.service;

import com.group1.car_rental.service.dto.MiniBankAuthorizeHoldResponse;
import com.group1.car_rental.service.dto.MiniBankCreatePaymentResponse;
import com.group1.car_rental.service.dto.MiniBankCaptureHoldResponse;
import com.group1.car_rental.service.dto.MiniBankGetPaymentResponse;
import com.group1.car_rental.service.dto.MiniBankVoidHoldResponse;

import java.util.UUID;

public interface MiniBankClient {

    MiniBankCreatePaymentResponse initPayment(String bookingRef, long totalPrice, String idempotencyKey);

    MiniBankAuthorizeHoldResponse authorizeHold(UUID paymentId, String idempotencyKey);

    MiniBankGetPaymentResponse getPayment(UUID paymentId);

    MiniBankCaptureHoldResponse captureHold(UUID holdId, String idempotencyKey);

    MiniBankVoidHoldResponse voidHold(UUID holdId, String idempotencyKey);
}