package com.group1.car_rental.repository;

import com.group1.car_rental.entity.ListingImages;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ListingImagesRepository extends JpaRepository<ListingImages, Long> {

    List<ListingImages> findByListingIdOrderBySortOrder(Long listingId);
}
