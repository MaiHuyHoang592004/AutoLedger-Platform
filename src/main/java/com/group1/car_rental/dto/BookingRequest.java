// src/main/java/com/group1/car_rental/dto/BookingRequest.java
package com.group1.car_rental.dto;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class BookingRequest {
    private Long listingId;
    private LocalDate pickupDate;
    private LocalTime pickupTime;
    private LocalDate returnDate;
    private LocalTime returnTime;
    private String message;
}