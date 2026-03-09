package com.group1.car_rental.repository;

import com.group1.car_rental.entity.AvailabilityCalendar;
import com.group1.car_rental.entity.AvailabilityCalendar.AvailabilityCalendarId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AvailabilityCalendarRepository extends JpaRepository<AvailabilityCalendar, AvailabilityCalendarId> {

    // Tìm tất cả slot của 1 listing trong khoảng ngày
    @Query("SELECT a FROM AvailabilityCalendar a " +
           "WHERE a.id.listingId = :listingId " +
           "AND a.id.day BETWEEN :startDate AND :endDate")
    List<AvailabilityCalendar> findByListingIdAndDateRange(
            @Param("listingId") Long listingId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // Tìm slot của 1 ngày cụ thể
    @Query("SELECT a FROM AvailabilityCalendar a " +
           "WHERE a.id.listingId = :listingId AND a.id.day = :day")
    AvailabilityCalendar findByListingIdAndDay(
            @Param("listingId") Long listingId,
            @Param("day") LocalDate day);

    // Tìm các slot theo listingId (dùng để xóa hoặc kiểm tra)
    List<AvailabilityCalendar> findByIdListingId(Long listingId);

    // Xóa tất cả slot của 1 listing (dùng khi refresh)
    void deleteByIdListingId(Long listingId);

    // Tìm các hold đã hết hạn (cho cron job)
    @Query("SELECT a FROM AvailabilityCalendar a " +
           "WHERE a.status = :status AND a.holdExpireAt < :now")
    List<AvailabilityCalendar> findByStatusAndHoldExpireAtBefore(
            @Param("status") String status,
            @Param("now") LocalDateTime now);

    // Tìm các slot theo hold token (khi xác nhận đặt chỗ)
    @Query("SELECT a FROM AvailabilityCalendar a WHERE a.holdToken = :holdToken")
    List<AvailabilityCalendar> findByHoldToken(@Param("holdToken") UUID holdToken);
}