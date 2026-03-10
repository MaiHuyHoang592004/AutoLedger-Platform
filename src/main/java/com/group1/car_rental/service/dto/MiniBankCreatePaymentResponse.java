package com.group1.car_rental.service.dto;

import java.util.UUID;

public record MiniBankCreatePaymentResponse(
    UUID paymentId,
    String status,
    String orderRef,
    long amountMinor,
    String currency) {
}