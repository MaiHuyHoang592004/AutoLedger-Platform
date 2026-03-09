package com.group1.car_rental.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.geolatte.geom.Point;  // ← ĐÚNG
import org.geolatte.geom.crs.CrsId;
import org.geolatte.geom.G2D;

import java.time.Instant;

@Entity
@Table(name = "car_listings")
@Data
@NoArgsConstructor
public class CarListings {

    public enum CancellationPolicy {
        STRICT, MODERATE, FLEXIBLE
    }

    public enum ListingStatus {
        ARCHIVED, SUSPENDED, ACTIVE, PENDING_REVIEW, DRAFT, APPROVED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Cars vehicle;

    @Column(name = "title", nullable = false, length = 140)
    private String title;

    @Column(name = "description", columnDefinition = "nvarchar(max)")
    private String description;

    @Column(name = "price_24h_cents", nullable = false)
    private Integer price24hCents;

    @Column(name = "km_limit_24h", nullable = false)
    private Integer kmLimit24h = 200;

    @Column(name = "instant_book", nullable = false)
    private Boolean instantBook = false;
    

    @Column(name = "cancellation_policy", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private CancellationPolicy cancellationPolicy = CancellationPolicy.MODERATE;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ListingStatus status = ListingStatus.DRAFT;

    @Column(columnDefinition = "geography")
    private String homeLocation;  // Lưu dạng WKT: "POINT(105.85 21.02)"
    @Column(name = "home_city", nullable = false, length = 20)
    private String homeCity;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;

    // Constructor
    public CarListings(Cars vehicle, String title, Integer price24hCents) {
        this.vehicle = vehicle;
        this.title = title;
        this.price24hCents = price24hCents;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.kmLimit24h = 200;
        this.instantBook = false;
        this.cancellationPolicy = CancellationPolicy.MODERATE;
        this.status = ListingStatus.PENDING_REVIEW;
    }
}