package com.group1.car_rental.repository;

import com.group1.car_rental.entity.Charges;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChargesRepository extends JpaRepository<Charges, Long> {

    List<Charges> findByBookingId(Long bookingId);
}
