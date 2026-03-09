package com.group1.car_rental.dto;

import java.util.List;

import lombok.Data;

@Data
public class CarsDto {
    private Long id;
    private String make;
    private String model;
    private Short year;
    private String transmission;
    private String fuelType;
    private Byte seats;
    private Double dailyPrice;
    private List<String> imageUrls;
    private String city;
    private Double rating;
    private Integer numReviews;
    private byte[] vinEncrypted;
    private String plateMasked;
}
