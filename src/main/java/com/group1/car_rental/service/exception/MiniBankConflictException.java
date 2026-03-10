package com.group1.car_rental.service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class MiniBankConflictException extends MiniBankException {

    public MiniBankConflictException(String message, String errorCode, int statusCode) {
        super(message, errorCode, statusCode);
    }
}