package com.group1.car_rental.service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class MiniBankInsufficientFundsException extends MiniBankException {

    public MiniBankInsufficientFundsException(String message, String errorCode, int statusCode) {
        super(message, errorCode, statusCode);
    }
}