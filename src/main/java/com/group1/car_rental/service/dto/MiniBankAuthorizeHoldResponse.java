package com.group1.car_rental.service.dto;

import java.time.Instant;
import java.util.UUID;

public record MiniBankAuthorizeHoldResponse(
    UUID holdId,
    UUID paymentId,
    int accountId,
    String status,
    long originalAmountMinor,
    long remainingAmountMinor,
    String currency,
    Instant expiresAtUtc) {
}