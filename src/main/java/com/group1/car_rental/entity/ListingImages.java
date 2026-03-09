package com.group1.car_rental.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "listing_images")
@Data
@NoArgsConstructor
public class ListingImages {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "listing_id", nullable = false)
    private CarListings listing;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public ListingImages(CarListings listing, String url, Integer sortOrder) {
        this.listing = listing;
        this.url = url;
        this.sortOrder = sortOrder;
        this.createdAt = Instant.now();
    }
}
