package com.group1.car_rental.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HostBookingDto {
    private Long id;
    private Instant startAt;
    private Instant endAt;
    private String status;
    private Instant createdAt;

    // Guest info
    private Long guestId;
    private String guestEmail;
    private String guestPhone;

    // Car info
    private Long carId;
    private String carMake;
    private String carModel;
    private Integer carYear;
    private List<String> carImageUrls;

    // Listing info
    private Long listingId;
    private Integer price24hCents;

    // Calculated fields
    private Integer totalDays;
    private Integer totalAmount;
    private Integer payoutAmount;
}
