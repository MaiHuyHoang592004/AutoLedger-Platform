package com.group1.car_rental.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "reviews")
@Data
@NoArgsConstructor
public class Reviews {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "booking_id", nullable = false)
    private Bookings booking;

    @ManyToOne
    @JoinColumn(name = "from_user_id", nullable = false)
    private User fromUser;

    @ManyToOne
    @JoinColumn(name = "to_user_id", nullable = false)
    private User toUser;

    @Column(nullable = false)
    private Byte rating;

    @Column(columnDefinition = "nvarchar(max)")
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Reviews(Bookings booking, User fromUser, User toUser, Byte rating) {
        this.booking = booking;
        this.fromUser = fromUser;
        this.toUser = toUser;
        this.rating = rating;
        this.createdAt = Instant.now();
    }

    // Backward compatibility constructor
    public Reviews(Bookings booking, User fromUser, Byte rating) {
        this.booking = booking;
        this.fromUser = fromUser;
        this.rating = rating;
        this.createdAt = Instant.now();
    }
}
