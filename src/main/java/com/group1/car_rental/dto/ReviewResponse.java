package com.group1.car_rental.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class ReviewResponse {
    private Long id;
    private String reviewerName;
    private Byte rating;
    private String comment;
    private LocalDateTime createdAt;
}
