package com.group1.car_rental.controller;

import com.group1.car_rental.dto.BookingDto;
import com.group1.car_rental.dto.HostBookingDto;
import com.group1.car_rental.entity.*;
import com.group1.car_rental.repository.BookingsRepository;
import com.group1.car_rental.repository.UserRepository;
import com.group1.car_rental.service.BookingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/host")
public class HostBookingController {

    private static final Logger logger = LoggerFactory.getLogger(HostBookingController.class);

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingsRepository bookingsRepository;

    @Autowired
    private UserRepository userRepository;

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

    // Host bookings page
    @GetMapping("/bookings")
    public String hostBookingsPage(Model model) {
        User currentUser = getCurrentUser();
        if (!"HOST".equals(currentUser.getRole())) {
            throw new RuntimeException("Only hosts can access this page");
        }

        // Add user info to model for Thymeleaf
        model.addAttribute("currentUser", currentUser);
        return "booking/host-bookings";
    }

    // API endpoint for host bookings
    @GetMapping("/api/bookings")
    public ResponseEntity<List<HostBookingDto>> getHostBookings() {
        try {
            User currentUser = getCurrentUser();
            logger.info("Current user: {} (ID: {}, Role: {})", currentUser.getEmail(), currentUser.getId(), currentUser.getRole());

            if (!"HOST".equals(currentUser.getRole())) {
                logger.warn("User {} is not a HOST, role: {}", currentUser.getEmail(), currentUser.getRole());
                throw new RuntimeException("Only hosts can access host bookings");
            }

            logger.info("Fetching bookings for host ID: {}", currentUser.getId());
            List<Bookings> bookings = bookingsRepository.findByListingVehicleOwnerId(currentUser.getId());
            logger.info("Found {} bookings for host {}", bookings.size(), currentUser.getEmail());

            // Convert to DTOs
            List<HostBookingDto> dtos = bookings.stream()
                .map(this::convertToHostBookingDto)
                .toList();

            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            logger.error("Error fetching host bookings", e);
            throw e;
        }
    }

    // API endpoint for single booking details (host view)
    @GetMapping("/api/bookings/{bookingId}")
    public ResponseEntity<BookingDto> getBooking(@PathVariable Long bookingId) {
        Bookings booking = bookingsRepository.findById(bookingId)
            .orElseThrow(() -> new RuntimeException("Booking not found"));

        User currentUser = getCurrentUser();
        // Check authorization - must be the host of this booking
        if (!booking.getListing().getVehicle().getOwner().getId().equals(currentUser.getId())) {
            throw new RuntimeException("You are not authorized to view this booking");
        }

        // Convert to DTO
        BookingDto dto = convertToDto(booking);
        return ResponseEntity.ok(dto);
    }

    // API endpoint for booking actions (host only)
    @PostMapping("/api/bookings/{bookingId}/{action}")
    public ResponseEntity<String> performBookingAction(
            @PathVariable Long bookingId,
            @PathVariable String action,
            @RequestHeader("Idempotency-Key") String idempotencyKeyStr) {

        Bookings booking = bookingsRepository.findById(bookingId)
            .orElseThrow(() -> new RuntimeException("Booking not found"));

        User currentUser = getCurrentUser();

        logger.info("Booking action attempt - Booking ID: {}, Action: {}, Current User: {} (ID: {}), User Role: {}",
            bookingId, action, currentUser.getEmail(), currentUser.getId(), currentUser.getRole());

        // Verify host ownership
        Long vehicleOwnerId = booking.getListing().getVehicle().getOwner().getId();
        logger.info("Booking {} belongs to vehicle owner ID: {}, Current user ID: {}",
            bookingId, vehicleOwnerId, currentUser.getId());

        if (!vehicleOwnerId.equals(currentUser.getId())) {
            logger.warn("Authorization failed: User {} (ID: {}) does not own vehicle for booking {} (owner ID: {})",
                currentUser.getEmail(), currentUser.getId(), bookingId, vehicleOwnerId);
            throw new RuntimeException("You do not own this vehicle");
        }

        if (!"HOST".equals(currentUser.getRole())) {
            logger.warn("Role check failed: User {} has role {}", currentUser.getEmail(), currentUser.getRole());
            throw new RuntimeException("Invalid role for this action");
        }

        UUID idempotencyKey = UUID.fromString(idempotencyKeyStr);

        switch (action) {
            case "confirm":
                bookingService.confirmBooking(bookingId, idempotencyKey);
                return ResponseEntity.ok("Booking confirmed");

            case "reject":
                bookingService.rejectBooking(bookingId, idempotencyKey);
                return ResponseEntity.ok("Booking rejected");

            default:
                throw new RuntimeException("Unknown action: " + action);
        }
    }

    // Helper method to convert Booking entity to DTO
    private BookingDto convertToDto(Bookings booking) {
        BookingDto dto = new BookingDto();
        dto.setId(booking.getId());
        dto.setStartAt(booking.getStartAt());
        dto.setEndAt(booking.getEndAt());
        dto.setStatus(booking.getStatus());
        dto.setCreatedAt(booking.getCreatedAt());
        dto.setUpdatedAt(booking.getUpdatedAt());

        // Convert guest
        BookingDto.UserDto guestDto = new BookingDto.UserDto();
        guestDto.setId(booking.getGuest().getId());
        guestDto.setEmail(booking.getGuest().getEmail());
        guestDto.setPhone(booking.getGuest().getPhone());
        dto.setGuest(guestDto);

        // Convert listing
        BookingDto.ListingDto listingDto = new BookingDto.ListingDto();
        listingDto.setId(booking.getListing().getId());
        listingDto.setHomeCity(booking.getListing().getHomeCity());
        listingDto.setPrice24hCents(booking.getListing().getPrice24hCents());
        listingDto.setInstantBook(booking.getListing().getInstantBook());
        listingDto.setCancellationPolicy(booking.getListing().getCancellationPolicy().toString());
        // Convert vehicle
        BookingDto.ListingDto.VehicleDto vehicleDto = new BookingDto.ListingDto.VehicleDto();
        vehicleDto.setId(booking.getListing().getVehicle().getId());
        vehicleDto.setMake(booking.getListing().getVehicle().getMake());
        vehicleDto.setModel(booking.getListing().getVehicle().getModel());
        vehicleDto.setYear(booking.getListing().getVehicle().getYear());
        vehicleDto.setTransmission(booking.getListing().getVehicle().getTransmission());
        vehicleDto.setFuelType(booking.getListing().getVehicle().getFuelType());
        vehicleDto.setSeats(booking.getListing().getVehicle().getSeats());
        vehicleDto.setImageUrls(booking.getListing().getVehicle().getImageUrls());

        // Convert owner
        BookingDto.ListingDto.VehicleDto.UserDto ownerDto = new BookingDto.ListingDto.VehicleDto.UserDto();
        ownerDto.setId(booking.getListing().getVehicle().getOwner().getId());
        ownerDto.setEmail(booking.getListing().getVehicle().getOwner().getEmail());
        ownerDto.setPhone(booking.getListing().getVehicle().getOwner().getPhone());
        vehicleDto.setOwner(ownerDto);

        listingDto.setVehicle(vehicleDto);
        dto.setListing(listingDto);

        return dto;
    }

    // Helper method to convert Booking entity to HostBookingDto
    private HostBookingDto convertToHostBookingDto(Bookings booking) {
        HostBookingDto dto = new HostBookingDto();
        dto.setId(booking.getId());
        dto.setStartAt(booking.getStartAt());
        dto.setEndAt(booking.getEndAt());
        dto.setStatus(booking.getStatus());
        dto.setCreatedAt(booking.getCreatedAt());

        // Guest info - check if guest is loaded
        if (booking.getGuest() != null) {
            dto.setGuestId(booking.getGuest().getId());
            dto.setGuestEmail(booking.getGuest().getEmail());
            dto.setGuestPhone(booking.getGuest().getPhone());
        }

        // Car info - check if listing and vehicle are loaded
        if (booking.getListing() != null && booking.getListing().getVehicle() != null) {
            dto.setCarId(booking.getListing().getVehicle().getId());
            dto.setCarMake(booking.getListing().getVehicle().getMake());
            dto.setCarModel(booking.getListing().getVehicle().getModel());
            dto.setCarYear(booking.getListing().getVehicle().getYear() != null ? booking.getListing().getVehicle().getYear().intValue() : null);
            dto.setCarImageUrls(booking.getListing().getVehicle().getImageUrls() != null ?
                booking.getListing().getVehicle().getImageUrls() : new ArrayList<>());
        }

        // Listing info
        if (booking.getListing() != null) {
            dto.setListingId(booking.getListing().getId());
            dto.setPrice24hCents(booking.getListing().getPrice24hCents());
        }

        // Calculate total days and amounts - sử dụng ChronoUnit.DAYS để tính chính xác theo ngày
        long totalDays = java.time.temporal.ChronoUnit.DAYS.between(
            booking.getStartAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
            booking.getEndAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate()) + 1;
        dto.setTotalDays((int) totalDays);

        int pricePerDay = dto.getPrice24hCents() != null ? dto.getPrice24hCents() : 0;
        int totalAmount = totalDays > 0 ? (int) (totalDays * pricePerDay) : 0;
        dto.setTotalAmount(totalAmount);

        // Calculate payout (90% after fees)
        int payoutAmount = (int) (totalAmount * 0.9);
        dto.setPayoutAmount(payoutAmount);

        return dto;
    }
}
