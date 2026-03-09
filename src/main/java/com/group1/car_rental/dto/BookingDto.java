package com.group1.car_rental.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
public class BookingDto {
    private Long id;
    private ListingDto listing;
    private UserDto guest;
    private Instant startAt;
    private Instant endAt;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @NoArgsConstructor
    public static class ListingDto {
        private Long id;
        private VehicleDto vehicle;
        private String homeCity;
        private Integer price24hCents;
        private Boolean instantBook;
        private String cancellationPolicy;

        @Data
        @NoArgsConstructor
        public static class VehicleDto {
            private Long id;
            private String make;
            private String model;
            private Short year;
            private String transmission;
            private String fuelType;
            private Byte seats;
            private List<String> imageUrls;
            private UserDto owner;

            @Data
            @NoArgsConstructor
            public static class UserDto {
                private Long id;
                private String email;
                private String phone;
            }
        }
    }

    @Data
    @NoArgsConstructor
    public static class UserDto {
        private Long id;
        private String email;
        private String phone;
    }
}
