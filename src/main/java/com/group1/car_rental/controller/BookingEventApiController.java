package com.group1.car_rental.controller;

import com.group1.car_rental.entity.OutboxEvents;
import com.group1.car_rental.repository.BookingsRepository;
import com.group1.car_rental.repository.OutboxEventsRepository;
import com.group1.car_rental.repository.UserRepository;
import com.group1.car_rental.entity.User;
import com.group1.car_rental.service.BookingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
public class BookingEventApiController {

    @Autowired
    private OutboxEventsRepository outboxEventsRepository;

    @Autowired
    private BookingsRepository bookingsRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookingService bookingService;

    // Helper method to get current authenticated user
    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }
        String email = auth.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @PostMapping("/{bookingId:\\d+}/checkin/guest/confirm")
    public ResponseEntity<String> guestAcknowledgeCheckIn(
            @PathVariable Long bookingId,
            @RequestHeader("Idempotency-Key") String idempotencyKeyStr) {

        User currentUser = getCurrentUser();
        var booking = bookingsRepository.findById(bookingId)
            .orElseThrow(() -> new RuntimeException("Booking not found"));

        // Only guest can acknowledge
        if (!booking.getGuest().getId().equals(currentUser.getId())) {
            throw new RuntimeException("Only the guest can acknowledge check-in");
        }

        var idempotencyKey = UUID.fromString(idempotencyKeyStr);
        // TODO: Call service method when available
        bookingService.guestAcknowledgeCheckIn(bookingId, idempotencyKey);

        return ResponseEntity.ok("Check-in acknowledged. Trip has started.");
    }

    @GetMapping("/{bookingId:\\d+}/events/{eventType}")
    public ResponseEntity<List<OutboxEvents>> getBookingEvents(
            @PathVariable Long bookingId,
            @PathVariable String eventType) {

        // Verify booking exists and user has access
        var booking = bookingsRepository.findById(bookingId)
            .orElseThrow(() -> new RuntimeException("Booking not found"));

        User currentUser = getCurrentUser();
        // Check authorization - guest or host can view
        if (!booking.getGuest().getId().equals(currentUser.getId()) &&
            !booking.getListing().getVehicle().getOwner().getId().equals(currentUser.getId())) {
            throw new RuntimeException("You are not authorized to view this booking");
        }

        List<OutboxEvents> events = outboxEventsRepository
            .findByAggregateTypeAndAggregateIdAndEventType("Booking", bookingId, eventType);

        return ResponseEntity.ok(events);
    }
}
