package com.group1.car_rental.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
public class ReviewCreateRequest {
    @Min(1)
    @Max(5)
    private Byte rating;

    @NotBlank
    private String comment;
}
