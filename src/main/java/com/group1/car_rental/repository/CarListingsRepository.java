package com.group1.car_rental.repository;

import com.group1.car_rental.entity.CarListings;
import com.group1.car_rental.entity.CityEnum;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CarListingsRepository extends JpaRepository<CarListings, Long> {
    boolean existsByVehicleIdAndStatus(Long vehicleId, CarListings.ListingStatus status);

    // Find active listings by city, ordered by price
    List<CarListings> findByHomeCityAndStatusOrderByPrice24hCentsAsc(CityEnum homeCity, String status);
    List<CarListings> findByHomeCityContainingIgnoreCaseAndStatus(String homeCity, CarListings.ListingStatus status);
    // Thêm method
    List<CarListings> findByVehicleOwnerId(Long ownerId);
    Optional<CarListings> findByIdAndVehicleOwnerId(Long id, Long ownerId);

    // Thêm
    List<CarListings> findByStatus(CarListings.ListingStatus status);
    List<CarListings> findByHomeCityAndStatus(String homeCity, CarListings.ListingStatus status);

    // Find available listings in city with date filtering
    @Query("SELECT DISTINCT l FROM CarListings l " +
           "JOIN AvailabilityCalendar a ON a.id.listingId = l.id " +
           "WHERE l.homeCity = :city " +
           "AND l.status = 'ACTIVE' " +
           "AND a.id.day BETWEEN :startDate AND :endDate " +
           "AND a.status = 'FREE' " +
           "GROUP BY l.id, l.title, l.price24hCents, l.homeCity, l.status, l.vehicle, l.description, " +
           "         l.kmLimit24h, l.instantBook, l.cancellationPolicy, l.homeLocation, l.createdAt, l.updatedAt, l.version " +
           "HAVING COUNT(a) = DATEDIFF(day, :startDate, :endDate) + 1")
    List<CarListings> findAvailableByCityAndDates(@Param("city") CityEnum city,
                                                 @Param("startDate") LocalDate startDate,
                                                 @Param("endDate") LocalDate endDate);

@Modifying
@Query("UPDATE CarListings l SET " +
       "l.status = :newStatus, " +
       "l.updatedAt = :updatedAt, " +
       "l.version = l.version + 1 " +
       "WHERE l.id = :id AND l.version = :version AND l.status = 'PENDING_REVIEW'")
int updateListingStatus(
        @Param("id") Long id,
        @Param("newStatus") CarListings.ListingStatus newStatus,
        @Param("updatedAt") Instant updatedAt,
        @Param("version") Integer version
);
    long countByHomeCityAndStatus(CityEnum homeCity, String status);
}