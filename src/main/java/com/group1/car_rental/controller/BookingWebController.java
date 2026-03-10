package com.group1.car_rental.controller;

import com.group1.car_rental.dto.BookingDto;

import com.group1.car_rental.entity.*;
import com.group1.car_rental.repository.AvailabilityCalendarRepository;
import com.group1.car_rental.repository.BookingsRepository;
import com.group1.car_rental.repository.CarListingsRepository;
import com.group1.car_rental.repository.OutboxEventsRepository;
import com.group1.car_rental.repository.TripInspectionsRepository;
import com.group1.car_rental.repository.UserRepository;
import com.group1.car_rental.service.BookingService;
import com.group1.car_rental.service.exception.MiniBankInsufficientFundsException;
import com.group1.car_rental.service.exception.MiniBankException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
@Controller
@RequestMapping("/bookings")
public class BookingWebController {

    private static final Logger logger = LoggerFactory.getLogger(BookingWebController.class);

    @Autowired
    private CarListingsRepository carListingsRepository;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingsRepository bookingsRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AvailabilityCalendarRepository availabilityCalendarRepository;

    @Autowired
    private OutboxEventsRepository outboxEventsRepository;

    @Autowired
    private TripInspectionsRepository tripInspectionsRepository;

    @Autowired
    private com.group1.car_rental.repository.ChargesRepository chargesRepository;

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

    // Show booking creation form
    @GetMapping("/create")
    public String showBookingForm(@RequestParam(required = false) Long listingId,
                                  @RequestParam(required = false) LocalDate pickupDate,
                                  @RequestParam(required = false) LocalDate returnDate,
                                  @RequestParam(required = false) String pickupTime,
                                  @RequestParam(required = false) String returnTime,
                                  @RequestParam(defaultValue = "false") boolean acceptedTerms,
                                  RedirectAttributes redirectAttributes,
                                  Model model) {

        // Check if user has accepted terms - Server-side validation
        if (!acceptedTerms) {
            logger.warn("SECURITY: Attempted booking creation without accepting terms: listingId={}, user={}",
                listingId, getCurrentUser().getEmail());
            redirectAttributes.addFlashAttribute("error", "Bạn cần đồng ý với điều khoản trước khi đặt xe");
            return "redirect:/cars/" + listingId;
        }


        if (listingId == null) {
            return "redirect:/search";
        }

        CarListings listing = carListingsRepository.findById(listingId)
                .orElseThrow(() -> new RuntimeException("Listing not found"));

        model.addAttribute("listing", listing);
        model.addAttribute("dailyRate", listing.getPrice24hCents());
        model.addAttribute("pickupDate", pickupDate);
        model.addAttribute("returnDate", returnDate);
        model.addAttribute("pickupTime", pickupTime);
        model.addAttribute("returnTime", returnTime);

        return "booking/create";
    }

    // Process booking creation - only hold slot, redirect to payment
    @PostMapping("/create")
    public Object createBooking(@RequestParam Long listingId,
                                @RequestParam LocalDate pickupDate,
                                @RequestParam LocalDate returnDate,
                                @RequestParam String pickupTime,
                                @RequestParam String returnTime,
                                @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                RedirectAttributes redirectAttributes) {

        User currentUser = getCurrentUser();
        logger.info("=== BOOKING HOLD START ===");
        logger.info("User: {} (ID: {}), Listing: {}, Dates: {} {} to {} {}",
                currentUser.getEmail(), currentUser.getId(), listingId,
                pickupDate, pickupTime, returnDate, returnTime);

        try {
            // COMPREHENSIVE BACKEND VALIDATION - Cannot be bypassed
            LocalDate today = LocalDate.now();

            // 1. không được trả đặt xe ở quá khứ
            if (pickupDate.isBefore(today)) {
                logger.warn("SECURITY: Attempted booking with past pickup date: {} (today: {})", pickupDate, today);
                redirectAttributes.addFlashAttribute("error", "Ngày nhận xe không được ở quá khứ");
                return "redirect:/bookings/create?listingId=" + listingId;
            }

            // 2. check để trả xe phải sau pick xe
            if (!returnDate.isAfter(pickupDate)) {
                logger.warn("SECURITY: Invalid date range - return date not after pickup: pickup={} return={}", pickupDate, returnDate);
                redirectAttributes.addFlashAttribute("error", "Ngày trả xe phải sau ngày nhận xe ít nhất 1 ngày");
                return "redirect:/bookings/create?listingId=" + listingId;
            }

            // 3. ứ cho đặt quá 3 tháng
            long daysDifference = java.time.temporal.ChronoUnit.DAYS.between(pickupDate, returnDate);
            if (daysDifference > 90) {
                logger.warn("SECURITY: Booking duration too long: {} days (max: 90)", daysDifference);
                redirectAttributes.addFlashAttribute("error", "Thời gian thuê xe không được vượt quá 90 ngày");
                return "redirect:/bookings/create?listingId=" + listingId;
            }

            // 4. đặt phải nhiều hơn 1 ngày
            if (daysDifference < 1) {
                logger.warn("SECURITY: Booking duration too short: {} days (min: 1)", daysDifference);
                redirectAttributes.addFlashAttribute("error", "Thời gian thuê xe phải ít nhất 1 ngày");
                return "redirect:/bookings/create?listingId=" + listingId;
            }

            // 5. Validate reasonable future booking (max 1 year ahead)
            LocalDate maxFutureDate = today.plusYears(1);
            if (pickupDate.isAfter(maxFutureDate)) {
                logger.warn("SECURITY: Booking too far in future: {} (max: {})", pickupDate, maxFutureDate);
                redirectAttributes.addFlashAttribute("error", "Không thể đặt xe quá 1 năm tính từ ngày hiện tại");
                return "redirect:/bookings/create?listingId=" + listingId;
            }

            // 6. Validate time formats and business hours
            LocalTime pickupLocalTime;
            LocalTime returnLocalTime;
            try {
                pickupLocalTime = LocalTime.parse(pickupTime);
                returnLocalTime = LocalTime.parse(returnTime);
            } catch (Exception e) {
                logger.warn("SECURITY: Invalid time format: pickupTime={} returnTime={}", pickupTime, returnTime);
                redirectAttributes.addFlashAttribute("error", "Định dạng thời gian không hợp lệ");
                return "redirect:/bookings/create?listingId=" + listingId;
            }

            // 7. phải trả xe giờ hành chính
            LocalTime businessStart = LocalTime.of(6, 0);
            LocalTime businessEnd = LocalTime.of(22, 0);
            if (pickupLocalTime.isBefore(businessStart) || pickupLocalTime.isAfter(businessEnd) ||
                returnLocalTime.isBefore(businessStart) || returnLocalTime.isAfter(businessEnd)) {
                logger.warn("SECURITY: Time outside business hours: pickup={} return={}", pickupLocalTime, returnLocalTime);
                redirectAttributes.addFlashAttribute("error", "Thời gian nhận/trả xe phải trong khung giờ 6:00 - 22:00");
                return "redirect:/bookings/create?listingId=" + listingId;
            }

            // 8. Validate listing exists and is active
            // CarListings validatedListing = carListingsRepository.findById(listingId)
            //         .orElseThrow(() -> new RuntimeException("Listing not found"));
            // if (!"ACTIVE".equals(validatedListing.getStatus())) {
            //     logger.warn("SECURITY: Attempted booking on inactive listing: {}", listingId);
            //     redirectAttributes.addFlashAttribute("error", "Xe này hiện không khả dụng để thuê");
            //     return "redirect:/bookings/create?listingId=" + listingId;
            // }

            // 9. Additional security: Check for rapid booking attempts (basic rate limiting)

            long currentTime = System.currentTimeMillis();
            String rateLimitKey = currentUser.getId() + ":" + listingId;

            logger.info("Booking attempt rate check: user={} listing={} time={}", currentUser.getId(), listingId, currentTime);
            // Generate idempotency key for hold operation
            UUID holdIdempotencyKey = UUID.randomUUID();
            logger.info("Generated hold key: {}", holdIdempotencyKey);

            // Hold slot only - no booking creation yet
            logger.info("Calling holdSlot...");
            UUID holdToken = bookingService.holdSlot(listingId, pickupDate, returnDate, holdIdempotencyKey, currentUser.getId());
            logger.info("Hold successful, token: {}", holdToken);

            // tính cái giá phải trả (bao gồm phí nền tảng 10%)
            CarListings listing = carListingsRepository.findById(listingId)
                    .orElseThrow(() -> new RuntimeException("Listing not found"));
            long days = java.time.temporal.ChronoUnit.DAYS.between(pickupDate, returnDate) + 1;
            int basePrice = (int) (days * listing.getPrice24hCents());
            int platformFee = (int) (basePrice * 0.1); // 10% phí nền tảng
            int totalAmount = basePrice + platformFee;

            // lưu bằng flash để dùng 1 lần cho payment
            redirectAttributes.addFlashAttribute("holdToken", holdToken.toString());
            redirectAttributes.addFlashAttribute("listingId", listingId);
            redirectAttributes.addFlashAttribute("pickupDate", pickupDate.toString());
            redirectAttributes.addFlashAttribute("returnDate", returnDate.toString());
            redirectAttributes.addFlashAttribute("pickupTime", pickupTime);
            redirectAttributes.addFlashAttribute("returnTime", returnTime);
            redirectAttributes.addFlashAttribute("totalAmount", totalAmount);
            redirectAttributes.addFlashAttribute("basePrice", basePrice);
            redirectAttributes.addFlashAttribute("platformFee", platformFee);
            redirectAttributes.addFlashAttribute("days", days);

            redirectAttributes.addFlashAttribute("success", "Đặt chỗ thành công! Vui lòng thanh toán để hoàn tất.");
            return "redirect:/bookings/payment/" + holdToken;

        } catch (Exception e) {
            logger.error("Booking hold failed", e);

            // User-friendly error messages based on exception type
            String userFriendlyMessage = getUserFriendlyErrorMessage(e);

            // Check if this is an AJAX request
            if ("XMLHttpRequest".equals(requestedWith)) {
                // Return JSON response for AJAX requests
                Map<String, Object> response = new java.util.HashMap<>();

                response.put("success", false);
                response.put("error", userFriendlyMessage);
                return ResponseEntity.badRequest().body(response);
            } else {
                // Traditional redirect for non-AJAX requests
                // Redirect back to booking create page with form data preserved
                // This allows user to see error and retry with same data
                redirectAttributes.addFlashAttribute("error", userFriendlyMessage);

                // Only add debug info in development mode (not in production)
                if (isDevelopmentEnvironment()) {
                    redirectAttributes.addFlashAttribute("debug", e.getMessage());
                }

                // Redirect back to booking create page with all form parameters preserved
                return "redirect:/bookings/create?listingId=" + listingId +
                       "&pickupDate=" + pickupDate +
                       "&returnDate=" + returnDate +
                       "&pickupTime=" + pickupTime +
                       "&returnTime=" + returnTime +
                       "&acceptedTerms=true";
            }
        }
    }

    // hiện payment page cho hold token
    @GetMapping("/payment/{holdToken}")
    public String showPaymentPage(@PathVariable String holdToken,
                                  @RequestParam(required = false) Long listingId,
                                  @RequestParam(required = false) String pickupDate,
                                  @RequestParam(required = false) String returnDate,
                                  @RequestParam(required = false) String pickupTime,
                                  @RequestParam(required = false) String returnTime,
                                  @RequestParam(required = false) Integer totalAmount,
                                  @RequestParam(required = false) Long days,
                                  Model model) {

        String pickupDateStr = null;
        String returnDateStr = null;

        try {
            UUID holdTokenUUID = UUID.fromString(holdToken);


            if (listingId != null && pickupDate != null && returnDate != null) {
                logger.info("Using flash attributes for payment page: listingId={}, pickupDate={}, returnDate={}",
                        listingId, pickupDate, returnDate);
                pickupDateStr = pickupDate;
                returnDateStr = returnDate;
            } else {
                // Otherwise, reconstruct from hold token in database
                logger.info("Reconstructing payment data from hold token: {}", holdToken);

                // Find availability records with this hold token
                List<AvailabilityCalendar> holdRecords = availabilityCalendarRepository
                        .findByHoldToken(holdTokenUUID);

                if (holdRecords.isEmpty()) {
                    logger.error("No hold records found for token: {}", holdToken);
                    model.addAttribute("error", "Không tìm thấy thông tin đặt chỗ. Hold token có thể đã hết hạn.");
                    return "booking/payment-hold";
                }

                // Get listing ID from first record
                Long reconstructedListingId = holdRecords.get(0).getId().getListingId();
                CarListings listing = carListingsRepository.findById(reconstructedListingId)
                        .orElseThrow(() -> new RuntimeException("Listing not found"));

                // Get date range from records
                LocalDate minDate = holdRecords.stream()
                        .map(cal -> cal.getId().getDay())
                        .min(LocalDate::compareTo)
                        .orElse(null);

                LocalDate maxDate = holdRecords.stream()
                        .map(cal -> cal.getId().getDay())
                        .max(LocalDate::compareTo)
                        .orElse(null);

                if (minDate == null || maxDate == null) {
                    logger.error("Could not determine date range for hold token: {}", holdToken);
                    model.addAttribute("error", "Không thể xác định thời gian đặt chỗ.");
                    return "booking/payment-hold";
                }

                // Calculate days and amount
                long calculatedDays = java.time.temporal.ChronoUnit.DAYS.between(minDate, maxDate) + 1;
                int calculatedBasePrice = (int) (calculatedDays * listing.getPrice24hCents());
                int calculatedPlatformFee = (int) (calculatedBasePrice * 0.1);
                int calculatedTotalAmount = calculatedBasePrice + calculatedPlatformFee;

                // Update variables
                listingId = reconstructedListingId;
                pickupDateStr = minDate.toString();
                returnDateStr = maxDate.toString();
                String reconstructedPickupTime = "09:00"; // Default
                String reconstructedReturnTime = "18:00"; // Default
                totalAmount = calculatedTotalAmount;
                days = calculatedDays;

                // Use reconstructed times if parameters were null
                if (pickupTime == null) pickupTime = reconstructedPickupTime;
                if (returnTime == null) returnTime = reconstructedReturnTime;

                logger.info("Reconstructed payment data: listing={}, dates={} to {}, amount={}",
                        listingId, pickupDateStr, returnDateStr, totalAmount);
            }

            // Add all data to model
            model.addAttribute("holdToken", holdToken);
            model.addAttribute("listingId", listingId);
            model.addAttribute("pickupDate", pickupDateStr);
            model.addAttribute("returnDate", returnDateStr);
            model.addAttribute("pickupTime", pickupTime != null ? pickupTime : "09:00");
            model.addAttribute("returnTime", returnTime != null ? returnTime : "18:00");
            model.addAttribute("totalAmount", totalAmount);
            model.addAttribute("basePrice", totalAmount - (int)(totalAmount * 0.1)); // basePrice = totalAmount - platformFee
            model.addAttribute("platformFee", (int)(totalAmount * 0.1)); // platformFee = 10% of totalAmount
            model.addAttribute("days", days);

            // Generate booking reference
            model.addAttribute("bookingRef", "BB" + System.currentTimeMillis() % 10000);

            // Get listing details for display
            if (listingId != null) {
                CarListings listing = carListingsRepository.findById(listingId).orElse(null);
                if (listing != null) {
                    model.addAttribute("listing", listing);
                    model.addAttribute("vehicleName", listing.getVehicle().getMake() + " " + listing.getVehicle().getModel());
                    model.addAttribute("location", listing.getHomeCity());
                }
            }

        } catch (Exception e) {
            logger.error("Error loading payment page for hold token: {}", holdToken, e);
            model.addAttribute("error", "Có lỗi xảy ra khi tải trang thanh toán: " + e.getMessage());
        }

        return "booking/payment-hold";
    }

    // Process payment and create booking
    @PostMapping("/payment/{holdToken}")
    public String processPayment(@PathVariable String holdToken,
                                 @RequestParam(required = false) Long listingId,
                                 @RequestParam(required = false) LocalDate pickupDate,
                                 @RequestParam(required = false) LocalDate returnDate,
                                 @RequestParam(required = false) String pickupTime,
                                 @RequestParam(required = false) String returnTime,
                                 @RequestParam String paymentMethod,
                                 RedirectAttributes redirectAttributes) {

        User currentUser = getCurrentUser();
        logger.info("=== PAYMENT PROCESSING START ===");
        logger.info("User: {} (ID: {}), HoldToken: {}", currentUser.getEmail(), currentUser.getId(), holdToken);

        try {
            // Parse hold token
            UUID holdTokenUUID = UUID.fromString(holdToken);

            // Use provided parameters or defaults
            String finalPickupTime = pickupTime != null ? pickupTime : "09:00";
            String finalReturnTime = returnTime != null ? returnTime : "18:00";

            // If parameters not provided, we need to reconstruct them from the hold
            if (listingId == null || pickupDate == null || returnDate == null) {
                logger.info("Reconstructing booking parameters from hold token: {}", holdToken);

                // Find availability records with this hold token
                List<AvailabilityCalendar> holdRecords = availabilityCalendarRepository
                        .findByHoldToken(holdTokenUUID);

                logger.info("Found {} hold records for token {}", holdRecords.size(), holdToken);

                if (holdRecords.isEmpty()) {
                    throw new RuntimeException("No hold records found for token: " + holdToken);
                }

                // Get listing ID from first record
                Long reconstructedListingId = holdRecords.get(0).getId().getListingId();

                // Get date range from records
                LocalDate minDate = holdRecords.stream()
                        .map(cal -> cal.getId().getDay())
                        .min(LocalDate::compareTo)
                        .orElse(null);

                LocalDate maxDate = holdRecords.stream()
                        .map(cal -> cal.getId().getDay())
                        .max(LocalDate::compareTo)
                        .orElse(null);

                logger.info("Reconstructed date range: minDate={}, maxDate={}", minDate, maxDate);

                if (minDate == null || maxDate == null) {
                    throw new RuntimeException("Could not determine date range for hold token: " + holdToken);
                }

                // Update variables
                listingId = reconstructedListingId;
                pickupDate = minDate;
                returnDate = maxDate;
                finalPickupTime = pickupTime != null ? pickupTime : "09:00";
                finalReturnTime = returnTime != null ? returnTime : "18:00";

                logger.info("Final reconstructed parameters: listingId={}, dates={} to {}, times={} to {}",
                        listingId, pickupDate, returnDate, finalPickupTime, finalReturnTime);
            } else {
                logger.info("Using provided parameters: listingId={}, dates={} to {}, times={} to {}",
                        listingId, pickupDate, returnDate, finalPickupTime, finalReturnTime);
            }

            // Parse times
            LocalTime pickupLocalTime = LocalTime.parse(finalPickupTime);
            LocalTime returnLocalTime = LocalTime.parse(finalReturnTime);

            // Combine dates and times
            LocalDateTime startDateTime = LocalDateTime.of(pickupDate, pickupLocalTime);
            LocalDateTime endDateTime = LocalDateTime.of(returnDate, returnLocalTime);

            // Convert to Instant using system timezone
            Instant startAt = startDateTime.atZone(ZoneId.systemDefault()).toInstant();
            Instant endAt = endDateTime.atZone(ZoneId.systemDefault()).toInstant();

            // Get listing
            CarListings listing = carListingsRepository.findById(listingId)
                    .orElseThrow(() -> new RuntimeException("Listing not found"));

            // Create booking object
            Bookings booking = new Bookings();
            booking.setListing(listing);
            booking.setGuest(currentUser);
            booking.setStartAt(startAt);
            booking.setEndAt(endAt);
            booking.setStatus("PENDING_HOST");

            // Generate idempotency key for booking creation
            UUID bookingIdempotencyKey = UUID.randomUUID();
            logger.info("Generated booking key: {}", bookingIdempotencyKey);

            // Create booking with hold token
            logger.info("Calling createBooking...");
            Bookings createdBooking = bookingService.createBooking(booking, holdTokenUUID, bookingIdempotencyKey);
            logger.info("Booking created successfully, ID: {}", createdBooking.getId());

            // Now authorize payment (always succeeds with mock provider)
            UUID paymentIdempotencyKey = UUID.randomUUID();
            logger.info("Authorizing payment for booking {}...", createdBooking.getId());

            Payments payment = bookingService.authorizePayment(createdBooking, "MOCK", "mock_payment_" + System.currentTimeMillis(), paymentIdempotencyKey);
            logger.info("Payment authorized successfully");

            logger.info("Payment processing completed successfully, redirecting to booking details");
            redirectAttributes.addFlashAttribute("success", "Thanh toán thành công! Đang chờ chủ xe xác nhận.");
            return "redirect:/bookings/" + createdBooking.getId() + "?success=true";

        } catch (Exception e) {
            logger.error("Payment processing failed for holdToken: {}, user: {}", holdToken, currentUser.getEmail(), e);
            logger.error("Exception details:", e);
            // For debugging, add the full exception to flash attributes
            redirectAttributes.addFlashAttribute("error", "Có lỗi xảy ra: " + e.getMessage());
            redirectAttributes.addFlashAttribute("debug", e.toString());

            // Add detailed debug info for web display
            StringBuilder debugInfo = new StringBuilder();
            debugInfo.append("Exception: ").append(e.getClass().getSimpleName()).append("\n");
            debugInfo.append("Message: ").append(e.getMessage()).append("\n");
            debugInfo.append("HoldToken: ").append(holdToken).append("\n");
            debugInfo.append("User: ").append(currentUser.getEmail()).append("\n");
            debugInfo.append("ListingId: ").append(listingId).append("\n");
            debugInfo.append("PickupDate: ").append(pickupDate).append("\n");
            debugInfo.append("ReturnDate: ").append(returnDate).append("\n");

            // Add database state info
            try {
                UUID holdTokenUUID = UUID.fromString(holdToken);
                List<AvailabilityCalendar> holdRecords = availabilityCalendarRepository
                    .findByHoldToken(holdTokenUUID);

                debugInfo.append("\n=== DATABASE STATE ===\n");
                debugInfo.append("Hold records found: ").append(holdRecords.size()).append("\n");

                for (int i = 0; i < holdRecords.size(); i++) {
                    AvailabilityCalendar cal = holdRecords.get(i);
                    debugInfo.append("Record ").append(i + 1).append(": ");
                    debugInfo.append("day=").append(cal.getId().getDay());
                    debugInfo.append(", status=").append(cal.getStatus());
                    debugInfo.append(", holdToken=").append(cal.getHoldToken());
                    debugInfo.append(", expireAt=").append(cal.getHoldExpireAt());
                    debugInfo.append("\n");
                }

                // Check if dates match
                if (pickupDate != null && returnDate != null) {
                    LocalDate start = pickupDate;
                    LocalDate end = returnDate;

                    debugInfo.append("\n=== DATE VALIDATION ===\n");
                    debugInfo.append("Expected dates: ").append(start).append(" to ").append(end).append("\n");

                    List<AvailabilityCalendar> dateRecords = availabilityCalendarRepository
                        .findByListingIdAndDateRange(listingId != null ? listingId : 1L, start, end);

                    debugInfo.append("Date range records found: ").append(dateRecords.size()).append("\n");

                    for (int i = 0; i < dateRecords.size(); i++) {
                        AvailabilityCalendar cal = dateRecords.get(i);
                        debugInfo.append("Date record ").append(i + 1).append(": ");
                        debugInfo.append("day=").append(cal.getId().getDay());
                        debugInfo.append(", status=").append(cal.getStatus());
                        debugInfo.append(", holdToken=").append(cal.getHoldToken());
                        debugInfo.append("\n");
                    }
                }

            } catch (Exception dbEx) {
                debugInfo.append("\n=== DATABASE ERROR ===\n");
                debugInfo.append("Failed to query database: ").append(dbEx.getMessage()).append("\n");
            }

            redirectAttributes.addFlashAttribute("debugInfo", debugInfo.toString());
            return "redirect:/bookings/payment/" + holdToken;
        }
    }

    // Show booking details (web page) - only match numeric IDs
    @GetMapping("/{bookingId:\\d+}")
    public String showBookingDetails(@PathVariable Long bookingId, Model model) {
        User currentUser = getCurrentUser();
        Bookings booking = bookingsRepository.findById(bookingId)
            .orElseThrow(() -> new RuntimeException("Booking not found"));

        // Check authorization - guest or host can view
        if (!booking.getGuest().getId().equals(currentUser.getId()) &&
            !booking.getListing().getVehicle().getOwner().getId().equals(currentUser.getId())) {
            throw new RuntimeException("Access denied");
        }

        // Calculate outstanding amount for surcharge payment
        int outstanding = bookingService.calculateOutstanding(bookingId);

        model.addAttribute("bookingId", bookingId);
        model.addAttribute("booking", booking);
        model.addAttribute("outstanding", outstanding);
        model.addAttribute("currentUser", currentUser);

        return "booking/booking-details";
    }

    // API endpoint for single booking details (customer/guest view)
    @GetMapping("/api/{bookingId:\\d+}")
    public ResponseEntity<BookingDto> getBooking(@PathVariable Long bookingId) {
        Bookings booking = bookingsRepository.findById(bookingId)
            .orElseThrow(() -> new RuntimeException("Booking not found"));

        User currentUser = getCurrentUser();
        // Check authorization - guest or host can view
        if (!booking.getGuest().getId().equals(currentUser.getId()) &&
            !booking.getListing().getVehicle().getOwner().getId().equals(currentUser.getId())) {
            throw new RuntimeException("You are not authorized to view this booking");
        }

        // Convert to DTO
        BookingDto dto = new BookingDto();
        dto.setId(booking.getId());
        dto.setStartAt(booking.getStartAt());
        dto.setEndAt(booking.getEndAt());
        dto.setStatus(booking.getStatus());
        dto.setCreatedAt(booking.getCreatedAt());
        dto.setUpdatedAt(booking.getUpdatedAt());

        // Set guest
        BookingDto.UserDto guestDto = new BookingDto.UserDto();
        guestDto.setId(booking.getGuest().getId());
        guestDto.setEmail(booking.getGuest().getEmail());
        guestDto.setPhone(booking.getGuest().getPhone());
        dto.setGuest(guestDto);

        // Set listing
        BookingDto.ListingDto listingDto = new BookingDto.ListingDto();
        listingDto.setId(booking.getListing().getId());
        listingDto.setHomeCity(booking.getListing().getHomeCity());
        listingDto.setPrice24hCents(booking.getListing().getPrice24hCents());
        listingDto.setInstantBook(booking.getListing().getInstantBook());
        listingDto.setCancellationPolicy(booking.getListing().getCancellationPolicy().toString());

        // Set vehicle
        BookingDto.ListingDto.VehicleDto vehicleDto = new BookingDto.ListingDto.VehicleDto();
        vehicleDto.setId(booking.getListing().getVehicle().getId());
        vehicleDto.setMake(booking.getListing().getVehicle().getMake());
        vehicleDto.setModel(booking.getListing().getVehicle().getModel());
        vehicleDto.setYear(booking.getListing().getVehicle().getYear());
        vehicleDto.setTransmission(booking.getListing().getVehicle().getTransmission());
        vehicleDto.setFuelType(booking.getListing().getVehicle().getFuelType());
        vehicleDto.setSeats(booking.getListing().getVehicle().getSeats());

        // Set owner
        BookingDto.ListingDto.VehicleDto.UserDto ownerDto = new BookingDto.ListingDto.VehicleDto.UserDto();
        ownerDto.setId(booking.getListing().getVehicle().getOwner().getId());
        ownerDto.setEmail(booking.getListing().getVehicle().getOwner().getEmail());
        ownerDto.setPhone(booking.getListing().getVehicle().getOwner().getPhone());
        vehicleDto.setOwner(ownerDto);

        listingDto.setVehicle(vehicleDto);
        dto.setListing(listingDto);

        return ResponseEntity.ok(dto);
    }

    // Show my bookings page (web page)
    @GetMapping("/my-bookings")
    public String showMyBookingsPage(Model model) {
        User currentUser = getCurrentUser();
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("currentUserRole", currentUser.getRole());
        return "booking/my-bookings";
    }

    // API endpoint for customer bookings
    @GetMapping("/api/my-bookings")
    public ResponseEntity<List<BookingDto>> getMyBookings() {
        User currentUser = getCurrentUser();
        logger.info("Fetching bookings for customer: {} (ID: {})", currentUser.getEmail(), currentUser.getId());

        List<Bookings> bookings = bookingsRepository.findByGuestId(currentUser.getId());
        logger.info("Found {} bookings for customer {}", bookings.size(), currentUser.getEmail());

        // Convert to DTOs
        List<BookingDto> dtos = bookings.stream()
            .map(booking -> {
                BookingDto dto = new BookingDto();
                dto.setId(booking.getId());
                dto.setStartAt(booking.getStartAt());
                dto.setEndAt(booking.getEndAt());
                dto.setStatus(booking.getStatus());
                dto.setCreatedAt(booking.getCreatedAt());
                dto.setUpdatedAt(booking.getUpdatedAt());

                // Set guest
                BookingDto.UserDto guestDto = new BookingDto.UserDto();
                guestDto.setId(booking.getGuest().getId());
                guestDto.setEmail(booking.getGuest().getEmail());
                guestDto.setPhone(booking.getGuest().getPhone());
                dto.setGuest(guestDto);

                // Set listing
                BookingDto.ListingDto listingDto = new BookingDto.ListingDto();
                listingDto.setId(booking.getListing().getId());
                listingDto.setHomeCity(booking.getListing().getHomeCity());
                listingDto.setPrice24hCents(booking.getListing().getPrice24hCents());
                listingDto.setInstantBook(booking.getListing().getInstantBook());
                listingDto.setCancellationPolicy(booking.getListing().getCancellationPolicy().toString());

                // Set vehicle
                BookingDto.ListingDto.VehicleDto vehicleDto = new BookingDto.ListingDto.VehicleDto();
                vehicleDto.setId(booking.getListing().getVehicle().getId());
                vehicleDto.setMake(booking.getListing().getVehicle().getMake());
                vehicleDto.setModel(booking.getListing().getVehicle().getModel());
                vehicleDto.setYear(booking.getListing().getVehicle().getYear());
                vehicleDto.setTransmission(booking.getListing().getVehicle().getTransmission());
                vehicleDto.setFuelType(booking.getListing().getVehicle().getFuelType());
                vehicleDto.setSeats(booking.getListing().getVehicle().getSeats());

                // Set owner
                BookingDto.ListingDto.VehicleDto.UserDto ownerDto = new BookingDto.ListingDto.VehicleDto.UserDto();
                ownerDto.setId(booking.getListing().getVehicle().getOwner().getId());
                ownerDto.setEmail(booking.getListing().getVehicle().getOwner().getEmail());
                ownerDto.setPhone(booking.getListing().getVehicle().getOwner().getPhone());
                vehicleDto.setOwner(ownerDto);

                listingDto.setVehicle(vehicleDto);
                dto.setListing(listingDto);

                return dto;
            })
            .toList();

        return ResponseEntity.ok(dtos);
    }

    // API endpoint to get outstanding amount for a booking
    @GetMapping("/api/{bookingId:\\d+}/outstanding")
    public ResponseEntity<Map<String, Object>> getOutstandingAmount(@PathVariable Long bookingId) {
        User currentUser = getCurrentUser();
        Bookings booking = bookingsRepository.findById(bookingId)
            .orElseThrow(() -> new RuntimeException("Booking not found"));

        // Check authorization - guest or host can view
        if (!booking.getGuest().getId().equals(currentUser.getId()) &&
            !booking.getListing().getVehicle().getOwner().getId().equals(currentUser.getId())) {
            throw new RuntimeException("Access denied");
        }

        int outstanding = bookingService.calculateOutstanding(bookingId);

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("bookingId", bookingId);
        response.put("outstanding", outstanding);

        return ResponseEntity.ok(response);
    }

    // API endpoint to get surcharge breakdown for a booking
    @GetMapping("/api/{bookingId:\\d+}/surcharges")
    public ResponseEntity<Map<String, Object>> getSurchargeBreakdown(@PathVariable Long bookingId) {
        User currentUser = getCurrentUser();
        Bookings booking = bookingsRepository.findById(bookingId)
            .orElseThrow(() -> new RuntimeException("Booking not found"));

        // Check authorization - guest or host can view
        if (!booking.getGuest().getId().equals(currentUser.getId()) &&
            !booking.getListing().getVehicle().getOwner().getId().equals(currentUser.getId())) {
            throw new RuntimeException("Access denied");
        }

        // Only show surcharges for COMPLETED bookings
        if (!"COMPLETED".equals(booking.getStatus())) {
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("bookingId", bookingId);
            response.put("hasSurcharges", false);
            response.put("surcharges", new java.util.ArrayList<>());
            response.put("totalSurcharges", 0);
            return ResponseEntity.ok(response);
        }

        // Get all charges for this booking
        List<com.group1.car_rental.entity.Charges> allCharges = chargesRepository.findByBookingId(bookingId);

        // Separate surcharges from initial booking charges
        List<Map<String, Object>> surcharges = new java.util.ArrayList<>();
        int totalSurcharges = 0;

        for (com.group1.car_rental.entity.Charges charge : allCharges) {
            String lineType = charge.getLineType();
            if ("FUEL".equals(lineType) || "KM_OVER".equals(lineType) || "CLEANING".equals(lineType)) {
                Map<String, Object> surcharge = new java.util.HashMap<>();
                surcharge.put("type", lineType);
                surcharge.put("amount", charge.getAmountCents());
                surcharge.put("note", charge.getNote());
                surcharges.add(surcharge);
                totalSurcharges += charge.getAmountCents();
            }
        }

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("bookingId", bookingId);
        response.put("hasSurcharges", !surcharges.isEmpty());
        response.put("surcharges", surcharges);
        response.put("totalSurcharges", totalSurcharges);

        return ResponseEntity.ok(response);
    }

    // API endpoint for booking actions (checkin, checkout, cancel for customers)
    @PostMapping("/api/{bookingId:\\d+}/{action}")
    public ResponseEntity<String> performBookingAction(
            @PathVariable Long bookingId,
            @PathVariable String action,
            @RequestParam(defaultValue = "false") boolean isHostCancellation,
            @RequestHeader("Idempotency-Key") String idempotencyKeyStr) {

        Bookings booking = bookingsRepository.findById(bookingId)
            .orElseThrow(() -> new RuntimeException("Booking not found"));

        User currentUser = getCurrentUser();

        UUID idempotencyKey = UUID.fromString(idempotencyKeyStr);

        switch (action) {
            case "checkin":
                // Guest can acknowledge check-in (not perform check-in directly)
                if (!booking.getGuest().getId().equals(currentUser.getId())) {
                    throw new RuntimeException("Only guest can acknowledge check-in");
                }
                bookingService.guestAcknowledgeCheckIn(bookingId, idempotencyKey);
                return ResponseEntity.ok("Check-in acknowledged");

            case "checkout":
                // Guest or host can check-out
                if (!booking.getGuest().getId().equals(currentUser.getId()) &&
                    !booking.getListing().getVehicle().getOwner().getId().equals(currentUser.getId())) {
                    throw new RuntimeException("You are not authorized to perform this action");
                }
                bookingService.completeTrip(bookingId, idempotencyKey);
                return ResponseEntity.ok("Trip completed");

            case "cancel":
                // Guest can cancel their own booking
                if (!booking.getGuest().getId().equals(currentUser.getId())) {
                    throw new RuntimeException("You can only cancel your own bookings");
                }
                bookingService.cancelBooking(bookingId, idempotencyKey, false);
                return ResponseEntity.ok("Booking cancelled");

            default:
                throw new RuntimeException("Unknown action: " + action);
        }
    }



    // Host check-in page
    @GetMapping("/{bookingId:\\d+}/checkin-host")
    public String showHostCheckInPage(@PathVariable Long bookingId, Model model) {
        User currentUser = getCurrentUser();
        Bookings booking = bookingsRepository.findById(bookingId)
            .orElseThrow(() -> new RuntimeException("Booking not found"));

        // Only host can access check-in page
        if (!booking.getListing().getVehicle().getOwner().getId().equals(currentUser.getId())) {
            throw new RuntimeException("Access denied");
        }

        // Check status
        if (!"PAYMENT_AUTHORIZED".equals(booking.getStatus())) {
            return "redirect:/bookings/" + bookingId;
        }

        model.addAttribute("bookingId", bookingId);
        model.addAttribute("booking", booking);
        return "booking/checkin-host";
    }

    // Extended host check-in with inspection data
    @PostMapping("/{bookingId:\\d+}/checkin-host")
    public String hostCheckIn(
            @PathVariable Long bookingId,
            @RequestParam Integer odometerKm,
            @RequestParam Byte fuelLevelPct,
            @RequestParam(required = false) List<MultipartFile> photos,
            @RequestParam(required = false) String notes,
            RedirectAttributes redirectAttributes) {

        User currentUser = getCurrentUser();
        Bookings booking = bookingsRepository.findById(bookingId)
            .orElseThrow(() -> new RuntimeException("Booking not found"));

        // Only host can perform check-in
        if (!booking.getListing().getVehicle().getOwner().getId().equals(currentUser.getId())) {
            throw new RuntimeException("Only the host can perform check-in");
        }

        try {
            // DEBUG: Log all input parameters
            logger.info("=== HOST CHECK-IN DEBUG START ===");
            logger.info("Booking ID: {}", bookingId);
            logger.info("Current User: {}", currentUser.getEmail());
            logger.info("Odometer: {}", odometerKm);
            logger.info("Fuel Level: {}", fuelLevelPct);
            logger.info("Notes: {}", notes);
            logger.info("Photos count: {}", photos != null ? photos.size() : 0);

            // Check booking status (already have booking variable from above)
            logger.info("Booking status: {}", booking.getStatus());

            // Validate inputs
            if (odometerKm == null || odometerKm < 0) {
                throw new RuntimeException("Số km hiện tại phải >= 0");
            }
            if (fuelLevelPct == null || fuelLevelPct < 0 || fuelLevelPct > 100) {
                throw new RuntimeException("Mức nhiên liệu phải từ 0-100%");
            }

            // Check if already checked in
            List<OutboxEvents> existingEvents = outboxEventsRepository
                .findByAggregateTypeAndAggregateIdAndEventType("Booking", bookingId, "CHECKIN_HOST");
            if (!existingEvents.isEmpty()) {
                throw new RuntimeException("Host đã check-in cho booking này rồi");
            }

            // Process photos and save to local uploads directory
            String photosJson = null; // NULL instead of "[]" to satisfy SQL constraint
            if (photos != null && !photos.isEmpty()) {
                List<String> photoUrls = new ArrayList<>();
                for (MultipartFile photo : photos) {
                    if (!photo.isEmpty()) {
                        try {
                            // Save file to src/main/resources/static/uploads/
                            String filename = UUID.randomUUID() + "_" + photo.getOriginalFilename();
                            Path uploadPath = Paths.get("src/main/resources/static/uploads");
                            Files.createDirectories(uploadPath);
                            Files.copy(photo.getInputStream(), uploadPath.resolve(filename), StandardCopyOption.REPLACE_EXISTING);

                            String photoUrl = "/uploads/" + filename;
                            photoUrls.add(photoUrl);
                            logger.info("Saved photo: {} -> {}", photo.getOriginalFilename(), filename);
                        } catch (IOException e) {
                            logger.error("Failed to save photo: {}", photo.getOriginalFilename(), e);
                            // Continue with other photos
                        }
                    }
                }
                if (!photoUrls.isEmpty()) {
                    // Use Jackson to create proper JSON array
                    try {
                        photosJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(photoUrls);
                        logger.info("Photos JSON for booking {}: {}", bookingId, photosJson);
                    } catch (Exception e) {
                        logger.error("Failed to serialize photo URLs to JSON", e);
                        // Fallback to null if JSON serialization fails
                        photosJson = null;
                    }
                }
            }

            UUID idempotencyKey = UUID.randomUUID(); // Generate idempotency key
            logger.info("Generated idempotency key: {}", idempotencyKey);

            bookingService.hostCheckIn(bookingId, odometerKm, fuelLevelPct, photosJson, notes, idempotencyKey);

            logger.info("=== HOST CHECK-IN SUCCESS ===");
            redirectAttributes.addFlashAttribute("success", "Check-in thành công! Đang chờ khách xác nhận.");
            return "redirect:/bookings/" + bookingId + "?checkin=success";

        } catch (Exception e) {
            logger.error("=== HOST CHECK-IN ERROR ===");
            logger.error("Booking ID: {}", bookingId);
            logger.error("User: {}", currentUser.getEmail());
            logger.error("Error message: {}", e.getMessage());
            logger.error("Error class: {}", e.getClass().getName());

            // Log full stack trace
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            logger.error("Full stack trace: {}", sw.toString());

            // Add debug info to flash attributes
            redirectAttributes.addFlashAttribute("debug_bookingId", bookingId);
            redirectAttributes.addFlashAttribute("debug_user", currentUser.getEmail());
            redirectAttributes.addFlashAttribute("debug_error", e.getMessage());
            redirectAttributes.addFlashAttribute("debug_errorClass", e.getClass().getName());
            redirectAttributes.addFlashAttribute("debug_stackTrace", sw.toString());

            redirectAttributes.addFlashAttribute("error", "Lỗi check-in: " + e.getMessage());
            return "redirect:/bookings/" + bookingId + "/checkin-host"; // Stay on form to retry
        }
    }

    // Guest check-in review page
    @GetMapping("/{bookingId:\\d+}/checkin-guest")
    public String showGuestCheckInReview(@PathVariable Long bookingId, Model model) {
        User currentUser = getCurrentUser();
        Bookings booking = bookingsRepository.findById(bookingId)
            .orElseThrow(() -> new RuntimeException("Booking not found"));

        // Only guest can access
        if (!booking.getGuest().getId().equals(currentUser.getId())) {
            throw new RuntimeException("Access denied");
        }

        // Check if host has checked in
        List<OutboxEvents> hostCheckInEvents = outboxEventsRepository
            .findByAggregateTypeAndAggregateIdAndEventType("Booking", bookingId, "CHECKIN_HOST");

        if (hostCheckInEvents.isEmpty()) {
            return "redirect:/bookings/" + bookingId; // Redirect if host hasn't checked in yet
        }

        // Get host inspection data
        List<TripInspections> inspections = tripInspectionsRepository.findByBooking_Id(bookingId);
        TripInspections hostInspection = inspections.stream()
            .filter(i -> "CHECKIN".equals(i.getChkType()))
            .findFirst()
            .orElse(null);

        // Parse photos JSON
        List<String> hostPhotos = new ArrayList<>();
        if (hostInspection != null && hostInspection.getPhotosJson() != null) {
            try {
                // Simple parsing - in real app, use Jackson
                String photosJson = hostInspection.getPhotosJson();
                if (photosJson.startsWith("[") && photosJson.endsWith("]")) {
                    String content = photosJson.substring(1, photosJson.length() - 1);
                    if (!content.trim().isEmpty()) {
                        for (String photo : content.split(",")) {
                            String cleanPhoto = photo.trim().replaceAll("^\"|\"$", "");
                            if (!cleanPhoto.isEmpty()) {
                                hostPhotos.add(cleanPhoto);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }

        model.addAttribute("bookingId", bookingId);
        model.addAttribute("booking", booking);
        model.addAttribute("hostInspection", hostInspection);
        model.addAttribute("hostPhotos", hostPhotos);

        return "booking/checkin-guest";
    }



    // Host check-out page
    @GetMapping("/{bookingId:\\d+}/checkout-host")
    public String showHostCheckOutPage(@PathVariable Long bookingId, Model model) {
        User currentUser = getCurrentUser();
        Bookings booking = bookingsRepository.findById(bookingId)
            .orElseThrow(() -> new RuntimeException("Booking not found"));

        // Only host can access check-out page
        if (!booking.getListing().getVehicle().getOwner().getId().equals(currentUser.getId())) {
            throw new RuntimeException("Access denied");
        }

        // Check status
        if (!"IN_PROGRESS".equals(booking.getStatus())) {
            return "redirect:/bookings/" + bookingId;
        }

        // Get check-in inspection data
        List<TripInspections> inspections = tripInspectionsRepository.findByBooking_Id(bookingId);
        TripInspections checkinInspection = inspections.stream()
            .filter(i -> "CHECKIN".equals(i.getChkType()))
            .findFirst()
            .orElse(null);

        // Calculate rental days consistently
        long rentalDays = java.time.temporal.ChronoUnit.DAYS.between(
            booking.getStartAt().atZone(ZoneId.systemDefault()).toLocalDate(),
            booking.getEndAt().atZone(ZoneId.systemDefault()).toLocalDate()) + 1;

        model.addAttribute("bookingId", bookingId);
        model.addAttribute("booking", booking);
        model.addAttribute("listing", booking.getListing());
        model.addAttribute("checkinInspection", checkinInspection);
        model.addAttribute("rentalDays", rentalDays);
        model.addAttribute("kmLimitPerDay", booking.getListing().getKmLimit24h());

        return "booking/checkout-host";
    }

    // Host check-out with charge calculation
    @PostMapping("/{bookingId:\\d+}/checkout-host")
    public String hostCheckOut(
            @PathVariable Long bookingId,
            @RequestParam Integer odometerKm,
            @RequestParam Byte fuelLevelPct,
            @RequestParam(required = false) List<MultipartFile> photos,
            @RequestParam(required = false) String notes,
            @RequestParam(defaultValue = "false") boolean needsCleaning,
            RedirectAttributes redirectAttributes) {

        User currentUser = getCurrentUser();
        Bookings booking = bookingsRepository.findById(bookingId)
            .orElseThrow(() -> new RuntimeException("Booking not found"));

        // Only host can perform check-out
        if (!booking.getListing().getVehicle().getOwner().getId().equals(currentUser.getId())) {
            throw new RuntimeException("Only the host can perform check-out");
        }

        try {
            // Process photos (simplified - in real app, upload to cloud storage)
            String photosJson = null; // NULL instead of "[]" to satisfy SQL constraint
            if (photos != null && !photos.isEmpty()) {
                List<String> photoUrls = new ArrayList<>();
                for (MultipartFile photo : photos) {
                    if (!photo.isEmpty()) {
                        // In real implementation, upload to S3/Cloudinary and get URL
                        String photoUrl = "/uploads/" + photo.getOriginalFilename();
                        photoUrls.add(photoUrl);
                    }
                }
                if (!photoUrls.isEmpty()) {
                    // Use Jackson to create proper JSON array
                    try {
                        photosJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(photoUrls);
                    } catch (Exception e) {
                        logger.error("Failed to serialize photo URLs to JSON", e);
                        // Fallback to null if JSON serialization fails
                        photosJson = null;
                    }
                }
            }

            UUID idempotencyKey = UUID.randomUUID(); // Generate idempotency key
            bookingService.hostCheckOut(bookingId, odometerKm, fuelLevelPct, photosJson, notes, needsCleaning, idempotencyKey);

            redirectAttributes.addFlashAttribute("success", "Check-out thành công! Chuyến đi đã hoàn tất.");
            return "redirect:/bookings/" + bookingId + "/summary";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi check-out: " + e.getMessage());
            return "redirect:/bookings/" + bookingId + "/checkout-host";
        }
    }

    // Trip summary page
    @GetMapping("/{bookingId:\\d+}/summary")
    public String showTripSummary(@PathVariable Long bookingId, Model model) {
        User currentUser = getCurrentUser();
        Bookings booking = bookingsRepository.findById(bookingId)
            .orElseThrow(() -> new RuntimeException("Booking not found"));

        // Check authorization - guest or host can view
        if (!booking.getGuest().getId().equals(currentUser.getId()) &&
            !booking.getListing().getVehicle().getOwner().getId().equals(currentUser.getId())) {
            throw new RuntimeException("Access denied");
        }

        // Check if booking is completed
        if (!"COMPLETED".equals(booking.getStatus())) {
            return "redirect:/bookings/" + bookingId;
        }

        // Get inspection data
        List<TripInspections> inspections = tripInspectionsRepository.findByBooking_Id(bookingId);
        TripInspections checkinInspection = inspections.stream()
            .filter(i -> "CHECKIN".equals(i.getChkType()))
            .findFirst()
            .orElse(null);

        TripInspections checkoutInspection = inspections.stream()
            .filter(i -> "CHECKOUT".equals(i.getChkType()))
            .findFirst()
            .orElse(null);

        // Parse photos
        List<String> checkinPhotos = parsePhotosJson(checkinInspection != null ? checkinInspection.getPhotosJson() : null);
        List<String> checkoutPhotos = parsePhotosJson(checkoutInspection != null ? checkoutInspection.getPhotosJson() : null);

        // Calculate charges (simplified - in real app, get from charges table)
        int baseAmount = (int) (booking.getListing().getPrice24hCents() *
            (java.time.temporal.ChronoUnit.DAYS.between(booking.getStartAt().atZone(ZoneId.systemDefault()).toLocalDate(), booking.getEndAt().atZone(ZoneId.systemDefault()).toLocalDate()) + 1));

        int kmOver = 0;
        int kmOverCharge = 0;
        int fuelCharge = 0;
        int cleaningCharge = 0;

        if (checkinInspection != null && checkoutInspection != null) {
            // KM calculation - Đồng nhất với BookingService
            int kmUsed = checkoutInspection.getOdometerKm() - checkinInspection.getOdometerKm();
            long rentalDays = java.time.temporal.ChronoUnit.DAYS.between(
                booking.getStartAt().atZone(ZoneId.systemDefault()).toLocalDate(),
                booking.getEndAt().atZone(ZoneId.systemDefault()).toLocalDate()) + 1;

            int kmLimit = booking.getListing().getKmLimit24h() * (int) rentalDays;
            kmOver = Math.max(0, kmUsed - kmLimit);
            kmOverCharge = kmOver * 5000; // 5000 VND/km

            // Fuel calculation
            int fuelUsed = checkinInspection.getFuelLevelPct() - checkoutInspection.getFuelLevelPct();
            if (fuelUsed > 0) {
                double litersMissing = (fuelUsed / 100.0) * 50; // Assume 50L tank
                fuelCharge = (int) Math.round(litersMissing * 25000); // 25000 VND/liter
            }

            // Cleaning charge (from checkout notes or separate field)
            if (checkoutInspection.getNotes() != null &&
                checkoutInspection.getNotes().toLowerCase().contains("dọn") ||
                checkoutInspection.getNotes().toLowerCase().contains("clean")) {
                cleaningCharge = 50000;
            }
        }

        int totalAmount = baseAmount + kmOverCharge + fuelCharge + cleaningCharge;

        // Tính tiền host nhận được (chỉ base - platform_fee, không bao gồm các khoản phát sinh)
        int hostEarnings = baseAmount - (int) (baseAmount * 0.1); // platform_fee = 10%

        model.addAttribute("bookingId", bookingId);
        model.addAttribute("booking", booking);
        model.addAttribute("checkinInspection", checkinInspection);
        model.addAttribute("checkoutInspection", checkoutInspection);
        model.addAttribute("checkinPhotos", checkinPhotos);
        model.addAttribute("checkoutPhotos", checkoutPhotos);
        model.addAttribute("baseAmount", baseAmount);
        model.addAttribute("kmOver", kmOver);
        model.addAttribute("kmOverCharge", kmOverCharge);
        model.addAttribute("fuelCharge", fuelCharge);
        model.addAttribute("cleaningCharge", cleaningCharge);
        model.addAttribute("totalAmount", totalAmount);
        model.addAttribute("hostEarnings", hostEarnings);
        model.addAttribute("isHostView", true); // Flag để template biết đây là view của host

        return "booking/summary";
    }

    // Pay outstanding surcharges
    @PostMapping("/{bookingId:\\d+}/pay-surcharge")
    public String paySurcharge(@PathVariable Long bookingId,
                              @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyStr,
                              RedirectAttributes redirectAttributes) {

        User currentUser = getCurrentUser();

        // Authorization check - only the booking guest can pay surcharges
        Bookings booking = bookingsRepository.findById(bookingId)
            .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (!booking.getGuest().getId().equals(currentUser.getId())) {
            logger.warn("Unauthorized surcharge payment attempt: user={} booking={} guest={}",
                currentUser.getId(), bookingId, booking.getGuest().getId());
            throw new RuntimeException("Access denied - only the booking guest can pay surcharges");
        }

        UUID idempotencyKey = idempotencyKeyStr != null ? UUID.fromString(idempotencyKeyStr) : UUID.randomUUID();

        try {
            bookingService.payOutstandingSurcharge(bookingId, currentUser, idempotencyKey);
            redirectAttributes.addFlashAttribute("success", "Thanh toán phụ phí thành công!");
        } catch (Exception e) {
            logger.error("Surcharge payment failed for booking {}: {}", bookingId, e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Lỗi thanh toán phụ phí: " + e.getMessage());
        }

        return "redirect:/bookings/" + bookingId;
    }

    // Accept charges (legacy - keep for compatibility)
    @PostMapping("/api/{bookingId:\\d+}/charges/accept")
    public ResponseEntity<String> acceptCharges(
            @PathVariable Long bookingId,
            @RequestHeader("Idempotency-Key") String idempotencyKeyStr) {

        User currentUser = getCurrentUser();
        Bookings booking = bookingsRepository.findById(bookingId)
            .orElseThrow(() -> new RuntimeException("Booking not found"));

        // Only guest can accept charges
        if (!booking.getGuest().getId().equals(currentUser.getId())) {
            throw new RuntimeException("Only the guest can accept charges");
        }

        // Create CHARGES_ACCEPTED event
        UUID idempotencyKey = UUID.fromString(idempotencyKeyStr);
        OutboxEvents event = new OutboxEvents("Booking", bookingId, "CHARGES_ACCEPTED", "{}");
        outboxEventsRepository.save(event);

        return ResponseEntity.ok("Charges accepted successfully");
    }

    // Show review form
    @GetMapping("/{bookingId:\\d+}/reviews")
    public String showReviewForm(@PathVariable Long bookingId, Model model) {
        User currentUser = getCurrentUser();
        Bookings booking = bookingsRepository.findById(bookingId)
            .orElseThrow(() -> new RuntimeException("Booking not found"));

        // Check if booking is completed
        if (!"COMPLETED".equals(booking.getStatus())) {
            return "redirect:/bookings/" + bookingId;
        }

        // Check if user is part of this booking
        boolean isGuest = booking.getGuest().getId().equals(currentUser.getId());
        boolean isHost = booking.getListing().getVehicle().getOwner().getId().equals(currentUser.getId());

        if (!isGuest && !isHost) {
            throw new RuntimeException("Access denied");
        }

        // Determine who to review
        Long toUserId;
        if (isGuest) {
            toUserId = booking.getListing().getVehicle().getOwner().getId();
        } else {
            toUserId = booking.getGuest().getId();
        }

        model.addAttribute("bookingId", bookingId);
        model.addAttribute("booking", booking);
        model.addAttribute("toUserId", toUserId);

        return "booking/review";
    }



    // Helper method to parse photos JSON
    private List<String> parsePhotosJson(String photosJson) {
        List<String> photos = new ArrayList<>();
        if (photosJson != null && !photosJson.trim().isEmpty()) {
            try {
                if (photosJson.startsWith("[") && photosJson.endsWith("]")) {
                    String content = photosJson.substring(1, photosJson.length() - 1);
                    if (!content.trim().isEmpty()) {
                        for (String photo : content.split(",")) {
                            String cleanPhoto = photo.trim().replaceAll("^\"|\"$", "");
                            if (!cleanPhoto.isEmpty()) {
                                photos.add(cleanPhoto);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }
        return photos;
    }

    /**
     * Convert technical exceptions to user-friendly error messages
     */
    private String getUserFriendlyErrorMessage(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return "Đã xảy ra lỗi không xác định. Vui lòng thử lại.";
        }

        if (e instanceof MiniBankInsufficientFundsException) {
            return "Số dư khả dụng không đủ để giữ tiền thanh toán. Vui lòng thử lại với số dư khác.";
        }

        if (e instanceof MiniBankException) {
            return "Hệ thống thanh toán MiniBank đang trả về lỗi. Vui lòng thử lại sau ít phút.";
        }

        // Map common technical errors to user-friendly messages
        if (message.contains("Not available")) {
            return "Xe này đã được đặt trong khoảng thời gian bạn chọn. Vui lòng chọn ngày khác.";
        } else if (message.contains("past pickup date")) {
            return "Không thể đặt xe ở thời điểm quá khứ. Vui lòng chọn ngày nhận xe từ hôm nay trở đi.";
        } else if (message.contains("return date not after pickup")) {
            return "Ngày trả xe phải sau ngày nhận xe ít nhất 1 ngày.";
        } else if (message.contains("duration too long")) {
            return "Thời gian thuê xe không được vượt quá 90 ngày.";
        } else if (message.contains("duration too short")) {
            return "Thời gian thuê xe phải ít nhất 1 ngày.";
        } else if (message.contains("too far in future")) {
            return "Không thể đặt xe quá 1 năm tính từ ngày hiện tại.";
        } else if (message.contains("Invalid time format")) {
            return "Định dạng thời gian không hợp lệ. Vui lòng kiểm tra lại giờ nhận/trả xe.";
        } else if (message.contains("business hours")) {
            return "Thời gian nhận/trả xe phải trong khung giờ 6:00 - 22:00.";
        } else if (message.contains("Listing not found")) {
            return "Xe không tồn tại hoặc đã bị xóa.";
        } else if (message.contains("User not found")) {
            return "Tài khoản của bạn không tồn tại. Vui lòng đăng nhập lại.";
        } else if (message.contains("not authenticated")) {
            return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
        } else if (message.contains("Duplicate")) {
            return "Yêu cầu trùng lặp. Vui lòng đợi một chút rồi thử lại.";
        } else if (message.contains("hold token expired")) {
            return "Thời gian giữ chỗ đã hết. Vui lòng tạo lại đơn đặt xe.";
        } else if (message.contains("Invalid hold token")) {
            return "Thông tin đặt chỗ không hợp lệ. Vui lòng tạo lại đơn đặt xe.";
        } else if (message.contains("KYC verification required")) {
            return "Tài khoản của bạn cần xác minh danh tính trước khi đặt xe.";
        } else if (message.contains("account is not active")) {
            return "Tài khoản của bạn chưa được kích hoạt.";
        } else if (message.contains("Only customers can place bookings")) {
            return "Chỉ tài khoản khách hàng mới có thể đặt xe.";
        } else if (message.contains("Only hosts can")) {
            return "Chỉ chủ xe mới có thể thực hiện thao tác này.";
        } else if (message.contains("Access denied")) {
            return "Bạn không có quyền thực hiện thao tác này.";
        } else if (message.contains("Invalid status")) {
            return "Trạng thái đơn đặt xe không cho phép thao tác này.";
        } else if (message.contains("already checked in")) {
            return "Đơn này đã được check-in rồi.";
        } else if (message.contains("already has successful")) {
            return "Đơn này đã được thanh toán thành công.";
        } else if (message.contains("No successful authorization")) {
            return "Không tìm thấy thông tin thanh toán cho đơn này.";
        } else if (message.contains("No hold records found")) {
            return "Không tìm thấy thông tin giữ chỗ. Hold token có thể đã hết hạn.";
        } else if (message.contains("Could not determine date range")) {
            return "Không thể xác định thời gian đặt chỗ.";
        } else {
            // Generic fallback for unknown errors
            return "Đã xảy ra lỗi trong quá trình xử lý. Vui lòng thử lại hoặc liên hệ hỗ trợ.";
        }
    }

    /**
     * Check if we're in development environment
     */
    private boolean isDevelopmentEnvironment() {
        String profile = System.getProperty("spring.profiles.active", "");
        return "dev".equals(profile) || "development".equals(profile) ||
               profile.contains("dev") || profile.contains("development");
    }

}
