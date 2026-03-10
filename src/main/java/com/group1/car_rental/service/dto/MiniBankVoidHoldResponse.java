package com.group1.car_rental.service.dto;

import java.util.UUID;

public record MiniBankVoidHoldResponse(
    UUID holdId,
    int voidStatus,
    String holdStatus) {
}