package com.group1.car_rental.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "payouts")
@Data
@NoArgsConstructor
public class Payouts {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne
    @JoinColumn(name = "booking_id", nullable = false)
    private Bookings booking;

    @Column(name = "amount_cents", nullable = false)
    private Integer amountCents;

    @Column(nullable = false, length = 3)
    private String currency = "VND";

    @Column(name = "bank_ref", length = 100)
    private String bankRef;

    @Column(nullable = false, length = 12)
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Payouts(User owner, Bookings booking, Integer amountCents) {
        this.owner = owner;
        this.booking = booking;
        this.amountCents = amountCents;
        this.createdAt = Instant.now();
    }
}
