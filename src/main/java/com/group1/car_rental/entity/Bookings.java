package com.group1.car_rental.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bookings")
@Data
@NoArgsConstructor
public class Bookings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "listing_id", nullable = false)
    private CarListings listing;

    @ManyToOne
    @JoinColumn(name = "guest_id", nullable = false)
    private User guest;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "pickup_location", columnDefinition = "geography")
    private Point pickupLocation;

    @Column(name = "dropoff_location", columnDefinition = "geography")
    private Point dropoffLocation;

    @Column(nullable = false, length = 24)
    private String status = "PENDING_HOST";

    @Column(name = "policy_snapshot", columnDefinition = "nvarchar(max)")
    private String policySnapshot;

    @Column(name = "price_snapshot", columnDefinition = "nvarchar(max)")
    private String priceSnapshot;

    @Column(name = "hold_token")
    private UUID holdToken;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Integer version = 0;

    public Bookings(CarListings listing, User guest, Instant startAt, Instant endAt) {
        this.listing = listing;
        this.guest = guest;
        this.startAt = startAt;
        this.endAt = endAt;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
