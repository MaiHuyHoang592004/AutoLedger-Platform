package com.group1.car_rental.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cars")
@Data
@NoArgsConstructor
public class Cars {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "vin_protected")
    private byte[] vinEncrypted;

    @Column(name = "plate_number")
    private String plateMasked;

    @Column(name = "make", nullable = false, length = 50)
    private String make;

    @Column(name = "model", nullable = false, length = 80)
    private String model;

    @Column(name = "year")
    private Short year;

    @Column(name = "transmission", nullable = false, length = 10)
    private String transmission;

    @Column(name = "fuel_type", nullable = false, length = 10)
    private String fuelType;

    @Column(name = "seats", nullable = false)
    private Byte seats;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "daily_price")
    private Double dailyPrice;

    @ElementCollection
    @CollectionTable(name = "car_images", joinColumns = @JoinColumn(name = "car_id"))
    @Column(name = "image_url")
    private List<String> imageUrls = new ArrayList<>();

    @Column(name = "city")
    private String city;

    @Column(name = "rating")
    private Double rating = 4.5;

    @Column(name = "num_reviews")
    private Integer numReviews = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Integer version;

    public Cars(User owner, String make, String model, String transmission, String fuelType) {
        this.owner = owner;
        this.make = make;
        this.model = model;
        this.transmission = transmission;
        this.fuelType = fuelType;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.status = "ACTIVE";
        this.seats = 5;
        this.imageUrls = new ArrayList<>();
    }
}
