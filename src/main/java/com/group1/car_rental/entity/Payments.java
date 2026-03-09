package com.group1.car_rental.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
public class Payments {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "booking_id", nullable = false)
    private Bookings booking;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(name = "amount_cents", nullable = false)
    private Integer amountCents;

    @Column(nullable = false, length = 3)
    private String currency = "VND";

    @Column(name = "provider", nullable = false, length = 40)
    private String provider;

    @Column(name = "provider_ref", length = 100)
    private String providerRef;

    @Column(nullable = false, length = 12)
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Payments(Bookings booking, String type, Integer amountCents, String provider) {
        this.booking = booking;
        this.type = type;
        this.amountCents = amountCents;
        this.provider = provider;
        this.createdAt = Instant.now();
    }
}
