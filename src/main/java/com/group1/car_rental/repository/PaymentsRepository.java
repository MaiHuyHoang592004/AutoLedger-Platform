package com.group1.car_rental.repository;

import com.group1.car_rental.entity.Payments;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentsRepository extends JpaRepository<Payments, Long> {

    List<Payments> findByBookingId(Long bookingId);

    // Find payments by booking, type and status
    List<Payments> findByBookingIdAndTypeAndStatus(Long bookingId, String type, String status);
}
