package com.group1.car_rental.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "addons")
@Data
@NoArgsConstructor
public class Addons {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "listing_id", nullable = false)
    private CarListings listing;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "charge_mode", nullable = false, length = 10)
    private String chargeMode;

    @Column(name = "price_cents", nullable = false)
    private Integer priceCents;

    public Addons(CarListings listing, String name, String chargeMode, Integer priceCents) {
        this.listing = listing;
        this.name = name;
        this.chargeMode = chargeMode;
        this.priceCents = priceCents;
    }
}
