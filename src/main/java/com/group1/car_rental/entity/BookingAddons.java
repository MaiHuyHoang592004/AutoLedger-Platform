package com.group1.car_rental.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "booking_addons")
@Data
@NoArgsConstructor
public class BookingAddons {

    @EmbeddedId
    private BookingAddonsId id;

    @Column(nullable = false)
    private Integer quantity = 1;

    public BookingAddons(BookingAddonsId id) {
        this.id = id;
    }

    @Embeddable
    @Data
    @NoArgsConstructor
    public static class BookingAddonsId implements java.io.Serializable {

        @Column(name = "booking_id", nullable = false)
        private Long bookingId;

        @Column(name = "extra_id", nullable = false)
        private Long extraId;

        public BookingAddonsId(Long bookingId, Long extraId) {
            this.bookingId = bookingId;
            this.extraId = extraId;
        }
    }
}
