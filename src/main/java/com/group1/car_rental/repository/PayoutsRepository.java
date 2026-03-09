package com.group1.car_rental.repository;

import com.group1.car_rental.entity.Payouts;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PayoutsRepository extends JpaRepository<Payouts, Long> {

    List<Payouts> findByOwnerId(Long ownerId);

    List<Payouts> findByBookingId(Long bookingId);

    List<Payouts> findByBookingIdAndStatus(Long bookingId, String status);
}
