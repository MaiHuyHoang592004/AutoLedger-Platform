package com.group1.car_rental.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
@Data
@NoArgsConstructor
public class IdempotencyKeys {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private UUID idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public IdempotencyKeys(UUID idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
        this.createdAt = Instant.now();
    }
}
