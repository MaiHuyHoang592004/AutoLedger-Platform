package com.group1.car_rental.service.dto;

public record MiniBankCreatePaymentRequest(
    String bookingId,
    long totalPrice) {
}