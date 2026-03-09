package com.group1.car_rental.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "trip_inspections")
@Data
@NoArgsConstructor
public class TripInspections {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "booking_id", nullable = false)
    private Bookings booking;

    @Column(name = "chk_type", nullable = false, length = 10)
    private String chkType;

    @Column(name = "odometer_km")
    private Integer odometerKm;

    @Column(name = "fuel_level_pct")
    private Byte fuelLevelPct;

    @Column(name = "photos_json", columnDefinition = "nvarchar(max)")
    private String photosJson;

    @Column(length = 255)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public TripInspections(Bookings booking, String chkType) {
        this.booking = booking;
        this.chkType = chkType;
        this.createdAt = Instant.now();
    }

    public Long getBookingId() {
        return booking != null ? booking.getId() : null;
    }
}
