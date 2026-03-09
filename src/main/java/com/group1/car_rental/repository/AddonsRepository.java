package com.group1.car_rental.repository;

import com.group1.car_rental.entity.Addons;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AddonsRepository extends JpaRepository<Addons, Long> {

    List<Addons> findByListingId(Long listingId);
}
