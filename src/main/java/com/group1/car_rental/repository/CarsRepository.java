package com.group1.car_rental.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.group1.car_rental.entity.Cars;
import com.group1.car_rental.entity.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface CarsRepository extends JpaRepository<Cars, Long> {

    List<Cars> findByOwner(User owner);

    @Query("SELECT c FROM Cars c WHERE c.id = :id AND c.owner = :owner")
    Optional<Cars> findByIdAndOwner(@Param("id") Long id, @Param("owner") User owner);

    @Query("SELECT c FROM Cars c WHERE LOWER(c.make) LIKE LOWER(CONCAT('%', :location, '%')) OR LOWER(c.model) LIKE LOWER(CONCAT('%', :location, '%'))")
    List<Cars> searchByLocation(@Param("location") String location);
}