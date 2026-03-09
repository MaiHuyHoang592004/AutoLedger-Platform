package com.group1.car_rental.service;

import com.group1.car_rental.entity.Bookings;

import java.util.List;

public interface IBookingService {
    List<Bookings> findAll();
    Bookings findById(Long id);
    Bookings save(Bookings booking);
    void updateStatus(Long id, String status);
    void deleteById(Long id);
    void processCodBooking(Long id);
    void releaseHold(Bookings booking);
    void cancelPayouts(Bookings booking);

    // THÊM 2 METHOD MỚI
    List<Bookings> findByGuestId(Long guestId);
    List<Bookings> findPendingByHostId(Long hostId);
    Bookings createBookingRequest(Bookings booking, java.util.UUID idempotencyKey);
}