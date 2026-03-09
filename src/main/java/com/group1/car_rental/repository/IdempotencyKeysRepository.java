package com.group1.car_rental.repository;

import com.group1.car_rental.entity.IdempotencyKeys;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdempotencyKeysRepository extends JpaRepository<IdempotencyKeys, Long> {

    Optional<IdempotencyKeys> findByIdempotencyKey(UUID idempotencyKey);
}
