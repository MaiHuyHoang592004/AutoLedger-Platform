package com.group1.car_rental.service.dto;

public record MiniBankApiErrorResponse(
    String title,
    Integer status,
    String detail,
    String code) {
}