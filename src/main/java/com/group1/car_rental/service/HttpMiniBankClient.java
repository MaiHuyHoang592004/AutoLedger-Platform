package com.group1.car_rental.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group1.car_rental.service.dto.MiniBankApiErrorResponse;
import com.group1.car_rental.service.dto.MiniBankAuthorizeHoldResponse;
import com.group1.car_rental.service.dto.MiniBankCaptureHoldResponse;
import com.group1.car_rental.service.dto.MiniBankCreatePaymentRequest;
import com.group1.car_rental.service.dto.MiniBankCreatePaymentResponse;
import com.group1.car_rental.service.dto.MiniBankGetPaymentResponse;
import com.group1.car_rental.service.dto.MiniBankVoidHoldResponse;
import com.group1.car_rental.service.exception.MiniBankConflictException;
import com.group1.car_rental.service.exception.MiniBankBadRequestException;
import com.group1.car_rental.service.exception.MiniBankException;
import com.group1.car_rental.service.exception.MiniBankInsufficientFundsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Service
public class HttpMiniBankClient implements MiniBankClient {

    private static final Logger logger = LoggerFactory.getLogger(HttpMiniBankClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public HttpMiniBankClient(
        RestTemplateBuilder restTemplateBuilder,
        ObjectMapper objectMapper,
        @Value("${minibank.api.base-url:http://localhost:5099}") String baseUrl) {
        this.restTemplate = restTemplateBuilder.build();
        this.objectMapper = objectMapper;
        this.baseUrl = stripTrailingSlash(baseUrl);
    }

    @Override
    public MiniBankCreatePaymentResponse initPayment(String bookingRef, long totalPrice, String idempotencyKey) {
        try {
            var request = new MiniBankCreatePaymentRequest(bookingRef, totalPrice);
            var response = restTemplate.exchange(
                baseUrl + "/api/payments",
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders(idempotencyKey)),
                MiniBankCreatePaymentResponse.class);

            return requireBody(response, "initPayment");
        } catch (HttpStatusCodeException ex) {
            throw mapException("initPayment", ex);
        }
    }

    @Override
    public MiniBankAuthorizeHoldResponse authorizeHold(UUID paymentId, String idempotencyKey) {
        try {
            var response = restTemplate.exchange(
                baseUrl + "/api/payments/" + paymentId + "/authorize-hold",
                HttpMethod.POST,
                new HttpEntity<>(null, jsonHeaders(idempotencyKey)),
                MiniBankAuthorizeHoldResponse.class);

            return requireBody(response, "authorizeHold");
        } catch (HttpStatusCodeException ex) {
            throw mapException("authorizeHold", ex);
        }
    }

    @Override
    public MiniBankGetPaymentResponse getPayment(UUID paymentId) {
        try {
            var response = restTemplate.exchange(
                baseUrl + "/api/payments/" + paymentId,
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(null)),
                MiniBankGetPaymentResponse.class);

            return requireBody(response, "getPayment");
        } catch (HttpStatusCodeException ex) {
            throw mapException("getPayment", ex);
        }
    }

    @Override
    public MiniBankCaptureHoldResponse captureHold(UUID holdId, String idempotencyKey) {
        try {
            var response = restTemplate.exchange(
                baseUrl + "/api/holds/" + holdId + "/capture",
                HttpMethod.POST,
                new HttpEntity<>(null, jsonHeaders(idempotencyKey)),
                MiniBankCaptureHoldResponse.class);

            return requireBody(response, "captureHold");
        } catch (HttpStatusCodeException ex) {
            throw mapException("captureHold", ex);
        }
    }

    @Override
    public MiniBankVoidHoldResponse voidHold(UUID holdId, String idempotencyKey) {
        try {
            var response = restTemplate.exchange(
                baseUrl + "/api/holds/" + holdId + "/void",
                HttpMethod.POST,
                new HttpEntity<>(null, jsonHeaders(idempotencyKey)),
                MiniBankVoidHoldResponse.class);

            return requireBody(response, "voidHold");
        } catch (HttpStatusCodeException ex) {
            throw mapException("voidHold", ex);
        }
    }

    private HttpHeaders jsonHeaders(String idempotencyKey) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return headers;
    }

    private <T> T requireBody(ResponseEntity<T> response, String operation) {
        if (response.getBody() == null) {
            throw new MiniBankException(
                "MiniBank returned an empty response for " + operation,
                "MINIBANK_EMPTY_RESPONSE",
                response.getStatusCode().value());
        }
        return response.getBody();
    }

    private MiniBankException mapException(String operation, HttpStatusCodeException ex) {
        MiniBankApiErrorResponse error = null;
        try {
            if (ex.getResponseBodyAsString() != null && !ex.getResponseBodyAsString().isBlank()) {
                error = objectMapper.readValue(ex.getResponseBodyAsString(), MiniBankApiErrorResponse.class);
            }
        } catch (Exception parseException) {
            logger.warn("Failed to parse MiniBank error response for {}: {}", operation, parseException.getMessage());
        }

        var detail = error != null && error.detail() != null && !error.detail().isBlank()
            ? error.detail()
            : "MiniBank call failed during " + operation;
        var code = error != null && error.code() != null && !error.code().isBlank()
            ? error.code()
            : "MINIBANK_HTTP_" + ex.getStatusCode().value();

        if ("INSUFFICIENT_FUNDS".equals(code)) {
            return new MiniBankInsufficientFundsException(detail, code, ex.getStatusCode().value());
        }

        if (ex.getStatusCode().value() == 409) {
            return new MiniBankConflictException(detail, code, ex.getStatusCode().value());
        }

        if (ex.getStatusCode().is4xxClientError()) {
            return new MiniBankBadRequestException(detail, code, ex.getStatusCode().value());
        }

        return new MiniBankException(detail, code, ex.getStatusCode().value());
    }

    private static String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:5099";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}