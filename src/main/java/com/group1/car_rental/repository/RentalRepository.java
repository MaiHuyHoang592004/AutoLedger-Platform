package com.group1.car_rental.repository;

import com.group1.car_rental.entity.Rental;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface RentalRepository extends JpaRepository<Rental, Long> {

    @Query("SELECT r FROM Rental r WHERE r.car.id = :carId AND r.status IN ('PENDING', 'CONFIRMED') AND " +
           "(r.startDate <= :endDate AND r.endDate >= :startDate)")
    List<Rental> findConflictingRentals(@Param("carId") Long carId,
                                       @Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate);
}