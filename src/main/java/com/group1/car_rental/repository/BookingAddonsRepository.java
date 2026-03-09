package com.group1.car_rental.repository;

import com.group1.car_rental.entity.BookingAddons;
import com.group1.car_rental.entity.BookingAddons.BookingAddonsId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingAddonsRepository extends JpaRepository<BookingAddons, BookingAddonsId> {

    List<BookingAddons> findByIdBookingId(Long bookingId);
}
