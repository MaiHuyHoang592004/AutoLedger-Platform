package com.group1.car_rental.dto;

import java.util.List;

import lombok.Data;

@Data
public class CarListingsDto {
    private Long id;
    private Long vehicleId;
    private String title;
    private String description;
    private Integer price24hCents;
    private Integer kmLimit24h;
    private Boolean instantBook;
    private String cancellationPolicy;
    private String status;
    private String homeCity;
    private Double longitude; // For display purposes
    private Double latitude;  // For display purposes
    private String make;
    private String model;
    private Short year;
    private List<String> imageUrls;
    private Double dailyPrice;
    private Double rating;
    private Integer numReviews;

    // Host information
    private String hostName;
    private String hostEmail;
    private Double hostRating;
    private Integer hostNumReviews;
}
