package com.group1.car_rental.service.dto;

import java.time.Instant;
import java.util.UUID;

public record MiniBankGetPaymentResponse(
    UUID paymentId,
    String paymentStatus,
    String orderRef,
    long amountMinor,
    String currency,
    UUID holdId,
    String holdStatus,
    Long remainingAmountMinor,
    Instant expiresAtUtc) {
}