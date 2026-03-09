package com.group1.car_rental.repository;

import com.group1.car_rental.entity.TripInspections;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TripInspectionsRepository extends JpaRepository<TripInspections, Long> {

    List<TripInspections> findByBooking_Id(Long bookingId);
}
