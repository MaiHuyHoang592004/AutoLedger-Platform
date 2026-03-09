package com.group1.car_rental.repository;

import com.group1.car_rental.entity.Reviews;
import com.group1.car_rental.entity.Bookings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewsRepository extends JpaRepository<Reviews, Long> {

    boolean existsByBooking(Bookings booking);

    List<Reviews> findAllByBooking_Listing_Id(Long listingId);

    List<Reviews> findAllByBooking_Listing_Vehicle_Id(Long carId);

    List<Reviews> findAllByFromUser_Id(Long userId);
}
