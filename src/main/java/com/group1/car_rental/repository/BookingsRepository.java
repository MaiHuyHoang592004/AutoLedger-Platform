package com.group1.car_rental.repository;

import com.group1.car_rental.entity.Bookings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface BookingsRepository extends JpaRepository<Bookings, Long> {

    @Query("SELECT b FROM Bookings b WHERE b.listing.id = :listingId AND b.startAt <= :endDate AND b.endAt >= :startDate")
    List<Bookings> findOverlappingBookings(@Param("listingId") Long listingId,
                                          @Param("startDate") Instant startDate,
                                          @Param("endDate") Instant endDate);

    @Query("SELECT b FROM Bookings b WHERE b.holdToken = :holdToken")
    Bookings findByHoldToken(@Param("holdToken") UUID holdToken);

    @Query("SELECT b FROM Bookings b WHERE b.guest.id = :guestId AND b.listing.id = :listingId AND b.startAt = :startAt AND b.endAt = :endAt")
    List<Bookings> findByGuestAndListingAndDates(@Param("guestId") Long guestId, @Param("listingId") Long listingId, @Param("startAt") Instant startAt, @Param("endAt") Instant endAt);

    List<Bookings> findByGuestId(Long guestId);

    @Query("SELECT b FROM Bookings b " +
           "JOIN FETCH b.guest g " +
           "JOIN FETCH b.listing l " +
           "JOIN FETCH l.vehicle v " +
           "WHERE v.owner.id = :ownerId")
    List<Bookings> findByListingVehicleOwnerId(@Param("ownerId") Long ownerId);

    @Query("SELECT b FROM Bookings b WHERE b.status = :status AND b.startAt < :startAtBefore")
    List<Bookings> findByStatusAndStartAtBefore(@Param("status") String status, @Param("startAtBefore") Instant startAtBefore);
}
