package com.group1.car_rental.service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class MiniBankBadRequestException extends MiniBankException {

    public MiniBankBadRequestException(String message, String errorCode, int statusCode) {
        super(message, errorCode, statusCode);
    }
}