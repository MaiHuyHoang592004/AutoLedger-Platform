package com.group1.car_rental.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CarListingsForm {
    private Long id;

    @NotNull(message = "Phải chọn xe")
    private Long vehicleId;

    @NotBlank(message = "Tiêu đề không được trống")
    @Size(max = 140, message = "Tiêu đề quá dài")
    private String title;

    @Size(max = 1000, message = "Mô tả quá dài")
    private String description;

    @NotNull(message = "Giá thuê không được trống")
    @Positive(message = "Giá thuê phải lớn hơn 0")
    private Integer price24hCents;

    @NotNull(message = "Giới hạn km không được trống")
    @Positive(message = "Giới hạn km phải lớn hơn 0")
    private Integer kmLimit24h = 200;

    private Boolean instantBook = false;

    private String cancellationPolicy = "MODERATE";

    // @NotBlank(message = "Trạng thái không được trống")
    // @Pattern(regexp = "ARCHIVED|SUSPENDED|ACTIVE|PENDING_REVIEW|DRAFT", message = "Trạng thái không hợp lệ")
    private String status = "PENDING_REVIEW";

    @NotBlank(message = "Thành phố không được trống")
    @Size(max = 20, message = "Tên thành phố quá dài")
    private String homeCity;

    @NotNull(message = "Kinh độ không được trống")
    private Double longitude;

    @NotNull(message = "Vĩ độ không được trống")
    private Double latitude;
}
