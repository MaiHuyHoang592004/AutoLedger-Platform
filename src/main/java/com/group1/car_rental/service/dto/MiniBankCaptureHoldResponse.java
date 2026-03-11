package com.group1.car_rental.service.dto;

import java.util.UUID;

public record MiniBankCaptureHoldResponse(
    UUID holdId,
    UUID paymentId,
    long capturedAmountMinor,
    long remainingAmountMinor,
    String currency,
    String holdStatus,
    String providerRef
) {
}