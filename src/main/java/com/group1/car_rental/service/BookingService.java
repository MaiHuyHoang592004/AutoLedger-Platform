package com.group1.car_rental.service;

import com.group1.car_rental.entity.*;
import com.group1.car_rental.entity.CarListings.ListingStatus;
import com.group1.car_rental.repository.*;
import com.group1.car_rental.service.dto.MiniBankAuthorizeHoldResponse;
import com.group1.car_rental.service.dto.MiniBankCaptureHoldResponse;
import com.group1.car_rental.service.dto.MiniBankCreatePaymentResponse;
import com.group1.car_rental.service.exception.MiniBankException;
import com.group1.car_rental.service.exception.MiniBankInsufficientFundsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class BookingService {

    private static final Logger logger = LoggerFactory.getLogger(BookingService.class);

    @Autowired
    private AvailabilityCalendarRepository availabilityCalendarRepository;

    @Autowired
    private BookingsRepository bookingsRepository;

    @Autowired
    private IdempotencyKeysRepository idempotencyKeysRepository;

    @Autowired
    private PaymentsRepository paymentsRepository;

    @Autowired
    private ChargesRepository chargesRepository;


    @Autowired
    private CarListingsRepository carListingsRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private PayoutsRepository payoutsRepository;

    @Autowired
    private PaymentProvider paymentProvider;

    @Autowired
    private MiniBankClient miniBankClient;

    @Autowired
    private TripInspectionsRepository tripInspectionsRepository;



    @Autowired
    private OutboxEventsRepository outboxEventsRepository;

    // Validation methods
    private void validateListingEligibility(Long listingId) {
        CarListings listing = carListingsRepository.findById(listingId)
            .orElseThrow(() -> new RuntimeException("Listing not found"));

        if (listing.getStatus() != ListingStatus.ACTIVE) {
            throw new RuntimeException("Listing is not active");
        }

        if (listing.getPrice24hCents() == null || listing.getPrice24hCents() <= 0) {
            throw new RuntimeException("Listing must have a valid daily price");
        }

        if (listing.getHomeLocation() == null) {
            throw new RuntimeException("Listing must have a valid home location");
        }

        if (listing.getHomeCity() == null) {
            throw new RuntimeException("Listing must have a valid home city");
        }
    }

    private void validateUserEligibility(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        if (!"CUSTOMER".equals(user.getRole())) {
            throw new RuntimeException("Only customers can place bookings");
        }

        if (!user.getIsActive()) {
            throw new RuntimeException("User account is not active");
        }

        UserProfile profile = user.getProfile();
        if (profile == null || !"VERIFIED".equals(profile.getKycStatus())) {
            throw new RuntimeException("KYC verification required to place bookings");
        }
    }

    private void validateHostEligibility(CarListings listing) {
        User owner = listing.getVehicle().getOwner();
        if (!"HOST".equals(owner.getRole())) {
            throw new RuntimeException("Listing owner must be a host");
        }

        if (!owner.getIsActive()) {
            throw new RuntimeException("Host account is not active");
        }
    }

    // 1. Check Availability
    public boolean checkAvailability(Long listingId, LocalDate startDate, LocalDate endDate) {
        // Validate listing eligibility first
        validateListingEligibility(listingId);

        List<AvailabilityCalendar> calendars = availabilityCalendarRepository
            .findByListingIdAndDateRange(listingId, startDate, endDate);

        // Create a set of dates that have records
        Set<LocalDate> existingDates = new HashSet<>();
        for (AvailabilityCalendar cal : calendars) {
            existingDates.add(cal.getId().getDay());
        }

        // Check all dates in range
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            // If date has a record, check its status
            if (existingDates.contains(current)) {
                for (AvailabilityCalendar cal : calendars) {
                    if (cal.getId().getDay().equals(current)) {
                        if ("HOLD".equals(cal.getStatus()) ||
                            "BOOKED".equals(cal.getStatus()) ||
                            "BLOCKED".equals(cal.getStatus())) {
                            return false; // Not available
                        }
                        break;
                    }
                }
            }
            // If date has no record, it's FREE (available)
            current = current.plusDays(1);
        }

        return true; // All dates are available
    }

    // 2. Hold Slot
    @Transactional
    public UUID holdSlot(Long listingId, LocalDate startDate, LocalDate endDate, UUID idempotencyKey, Long userId) {
        logger.info("Hold placed: token={} listing={} by user={} from {} to {}",
            idempotencyKey, listingId, userId, startDate, endDate);

        // Validate user eligibility first
        validateUserEligibility(userId);

        // Check idempotency
        if (idempotencyKeysRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            logger.warn("Duplicate hold request detected: token={} user={}", idempotencyKey, userId);
            throw new RuntimeException("Duplicate request");
        }

        // Save idempotency key
        idempotencyKeysRepository.save(new IdempotencyKeys(idempotencyKey));

        // Check availability (includes listing validation)
        if (!checkAvailability(listingId, startDate, endDate)) {
            logger.warn("Hold failed - not available: listing={} user={} from {} to {}",
                listingId, userId, startDate, endDate);
            throw new RuntimeException("Not available");
        }

        // Generate hold token
        UUID holdToken = UUID.randomUUID();

        // Set HOLD status
        LocalDate current = startDate;
        int daysHeld = 0;
        while (!current.isAfter(endDate)) {
            AvailabilityCalendar cal = availabilityCalendarRepository
                .findByListingIdAndDay(listingId, current);
            if (cal == null) {
                cal = new AvailabilityCalendar(new AvailabilityCalendar.AvailabilityCalendarId(listingId, current));
            }
            cal.setStatus("HOLD");
            cal.setHoldToken(holdToken);
            cal.setHoldExpireAt(LocalDateTime.now().plusMinutes(15));
            availabilityCalendarRepository.save(cal);
            current = current.plusDays(1);
            daysHeld++;
        }

        logger.info("Hold successful: token={} listing={} user={} days={} expires={}",
            holdToken, listingId, userId, daysHeld, LocalDateTime.now().plusMinutes(15));

        return holdToken;
    }

    // 3. Create Booking
    @Transactional
    public Bookings createBooking(Bookings booking, UUID holdToken, UUID idempotencyKey) {
        // Calculate days consistently with controller (inclusive end date)
        long days = java.time.temporal.ChronoUnit.DAYS.between(
            booking.getStartAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
            booking.getEndAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate()) + 1;

        // Safety check: ensure days is positive to prevent negative pricing
        if (days <= 0) {
            logger.error("Invalid booking duration: {} days for booking start={} end={}",
                days, booking.getStartAt(), booking.getEndAt());
            throw new RuntimeException("Invalid booking duration: return date must be after pickup date");
        }

        // Maximum duration check (90 days)
        if (days > 90) {
            logger.error("Booking duration too long: {} days (max 90)", days);
            throw new RuntimeException("Booking duration cannot exceed 90 days");
        }

        int totalAmount = calculateBookingTotal(booking);

        logger.info("=== CREATE BOOKING START ===");
        logger.info("Booking details: listing={} user={} holdToken={} idempotencyKey={}",
            booking.getListing().getId(), booking.getGuest().getId(), holdToken, idempotencyKey);
        logger.info("Dates: {} to {}, days={}, amount={}",
            booking.getStartAt(), booking.getEndAt(), days, totalAmount);

        // FIRST: Check idempotency key to prevent race conditions
        boolean keyExists = idempotencyKeysRepository.findByIdempotencyKey(idempotencyKey).isPresent();
        logger.info("Idempotency key check: key={} exists={}", idempotencyKey, keyExists);

        if (keyExists) {
            logger.warn("Duplicate booking creation request detected: token={}", idempotencyKey);
            throw new RuntimeException("Duplicate booking creation request");
        }

        // SECOND: Check if booking already exists (idempotent check)
        List<Bookings> existingBookings = bookingsRepository.findByGuestAndListingAndDates(
            booking.getGuest().getId(),
            booking.getListing().getId(),
            booking.getStartAt(),
            booking.getEndAt()
        );

        if (!existingBookings.isEmpty()) {
            logger.info("Found existing booking for idempotent response: bookingId={}", existingBookings.get(0).getId());
            return existingBookings.get(0);
        }

        // Save idempotency key
        logger.info("Saving idempotency key: {}", idempotencyKey);
        idempotencyKeysRepository.save(new IdempotencyKeys(idempotencyKey));

        // Validate user eligibility
        validateUserEligibility(booking.getGuest().getId());

        // Validate host eligibility
        validateHostEligibility(booking.getListing());

        // Validate hold token
        Bookings existingHold = bookingsRepository.findByHoldToken(holdToken);
        if (existingHold != null) {
            logger.error("Hold token already used: token={} existingBooking={}", holdToken, existingHold.getId());
            throw new RuntimeException("Hold token already used");
        }

        // Validate availability with hold token and check expiration
        LocalDate startDate = booking.getStartAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        LocalDate endDate = booking.getEndAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate();

        logger.info("Validating hold token for dates: {} to {}, listing: {}", startDate, endDate, booking.getListing().getId());

        List<AvailabilityCalendar> calendars = availabilityCalendarRepository
            .findByListingIdAndDateRange(booking.getListing().getId(), startDate, endDate);

        logger.info("Found {} calendar records for validation", calendars.size());

        LocalDateTime now = LocalDateTime.now();
        logger.info("Current time for validation: {}", now);

        for (AvailabilityCalendar cal : calendars) {
            logger.info("Calendar record: day={}, status={}, holdToken={}, expireAt={}",
                cal.getId().getDay(), cal.getStatus(), cal.getHoldToken(), cal.getHoldExpireAt());

            // Check status and token
            boolean statusValid = "HOLD".equals(cal.getStatus());
            boolean tokenValid = holdToken.equals(cal.getHoldToken());

            logger.info("Validation check for record {}: statusValid={}, tokenValid={}", cal.getId().getDay(), statusValid, tokenValid);

            if (!statusValid || !tokenValid) {
                String errorMsg = String.format(
                    "Invalid hold token validation for record %s: expected token=%s status=HOLD, actual token=%s status=%s, statusValid=%s, tokenValid=%s",
                    cal.getId().getDay(), holdToken, cal.getHoldToken(), cal.getStatus(), statusValid, tokenValid);
                logger.error(errorMsg);
                throw new RuntimeException("Invalid hold token: " + errorMsg);
            }

            // Check if hold has expired
            if (cal.getHoldExpireAt() != null) {
                boolean isExpired = cal.getHoldExpireAt().isBefore(now);
                logger.info("Expiration check for record {}: expireAt={}, now={}, isExpired={}",
                    cal.getId().getDay(), cal.getHoldExpireAt(), now, isExpired);

                if (isExpired) {
                    logger.error("Hold token expired: token={} expiredAt={}", holdToken, cal.getHoldExpireAt());
                    throw new RuntimeException("Hold token expired");
                }
            } else {
                logger.warn("Hold expire time is null for record: {}", cal.getId().getDay());
            }
        }

        logger.info("Hold token validation passed for {} records", calendars.size());

        // Set booking status - always require host approval
        booking.setStatus("PENDING_HOST");
        logger.info("Booking {} pending host confirmation", booking.getId());

        booking.setHoldToken(holdToken);
        booking.setCreatedAt(Instant.now());
        booking.setUpdatedAt(Instant.now());

        Bookings savedBooking = bookingsRepository.save(booking);

        // Publish outbox event
        outboxEventsRepository.save(new OutboxEvents("Booking", savedBooking.getId(),
            "BOOKING_CREATED", "{\"bookingId\": " + savedBooking.getId() + ", \"totalAmount\": " + totalAmount + ", \"days\": " + days + "}"));

        logger.info("Booking {} successfully created and event published", savedBooking.getId());

        return savedBooking;
    }

    // 4. Authorize Payment
    @Transactional
    public Payments authorizePayment(Bookings booking, String provider, String providerRef, UUID idempotencyKey) {
        int totalAmount = calculateBookingTotal(booking);

        logger.info("Authorizing payment for booking {}: amount={} provider={}",
            booking.getId(), totalAmount, provider);

        // Check idempotency
        if (idempotencyKeysRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            logger.warn("Duplicate payment authorization request: booking={} token={}",
                booking.getId(), idempotencyKey);
            throw new RuntimeException("Duplicate payment authorization request");
        }

        // Save idempotency key
        idempotencyKeysRepository.save(new IdempotencyKeys(idempotencyKey));

        // Check for existing successful AUTH
        List<Payments> existingAuths = paymentsRepository.findByBookingIdAndTypeAndStatus(booking.getId(), "AUTH", "SUCCEEDED");
        if (!existingAuths.isEmpty()) {
            logger.warn("Booking {} already has successful authorization", booking.getId());
            throw new RuntimeException("Booking already has a successful authorization");
        }

        final MiniBankCreatePaymentResponse initPaymentResponse;
        final MiniBankAuthorizeHoldResponse authorizeHoldResponse;
        try {
            initPaymentResponse = miniBankClient.initPayment(
                buildMiniBankOrderRef(booking),
                totalAmount,
                buildBookingPaymentInitIdempotencyKey(booking.getId()));

            authorizeHoldResponse = miniBankClient.authorizeHold(
                initPaymentResponse.paymentId(),
                buildBookingAuthorizeIdempotencyKey(booking.getId()));
        } catch (MiniBankInsufficientFundsException ex) {
            logger.warn("MiniBank insufficient funds for booking {}: {}", booking.getId(), ex.getMessage());
            throw ex;
        } catch (MiniBankException ex) {
            logger.error("MiniBank authorization failed for booking {} with code {}: {}",
                booking.getId(), ex.getErrorCode(), ex.getMessage());
            throw ex;
        }

        Payments savedPayment;
        try {
            booking.setPaymentId(initPaymentResponse.paymentId());
            booking.setHoldId(authorizeHoldResponse.holdId());
            booking.setPaymentProvider("MINIBANK");
            bookingsRepository.save(booking);

            Payments payment = new Payments(booking, "AUTH", totalAmount, "MINIBANK");
            payment.setProviderRef(initPaymentResponse.paymentId().toString());
            payment.setStatus("SUCCEEDED");
            payment.setCreatedAt(Instant.now());
            savedPayment = paymentsRepository.save(payment);
        } catch (RuntimeException ex) {
            compensateAuthorizedHold(booking, authorizeHoldResponse.holdId(), idempotencyKey, ex);
            throw ex;
        }

        logger.info("MiniBank AUTH succeeded for booking {}: amount={} paymentId={} holdId={}",
            booking.getId(), totalAmount, initPaymentResponse.paymentId(), authorizeHoldResponse.holdId());

        // Create charges breakdown after successful AUTH
        createChargesBreakdown(booking, totalAmount);

        // Update booking status - keep PENDING_HOST for host approval
        if ("INSTANT_CONFIRMED".equals(booking.getStatus())) {
            booking.setStatus("PAYMENT_AUTHORIZED");
            bookingsRepository.save(booking);
            logger.info("Booking {} status updated to PAYMENT_AUTHORIZED", booking.getId());
        }
        // If PENDING_HOST, keep status for host approval after payment

        // Publish outbox event
        outboxEventsRepository.save(new OutboxEvents("Payment", savedPayment.getId(),
            "PAYMENT_AUTHORIZED", "{\"bookingId\": " + booking.getId() + ", \"amount\": " + totalAmount + ", \"provider\": \"MINIBANK\"}"));

        return savedPayment;
    }

    private String buildMiniBankOrderRef(Bookings booking) {
        if (booking.getId() == null) {
            throw new IllegalStateException("Booking must be persisted before MiniBank payment initialization.");
        }
        return "BOOKING-" + booking.getId();
    }

    private String buildBookingPaymentInitIdempotencyKey(Long bookingId) {
        return "booking-" + bookingId + "-payment-init";
    }

    private String buildBookingAuthorizeIdempotencyKey(Long bookingId) {
        return "booking-" + bookingId + "-authorize";
    }

    private void compensateAuthorizedHold(Bookings booking, UUID holdId, UUID idempotencyKey, RuntimeException originalException) {
        try {
            logger.warn("Compensating MiniBank hold for booking {} using holdId={} due to local persistence failure",
                booking.getId(), holdId);
            miniBankClient.voidHold(holdId, buildBookingVoidIdempotencyKey(booking.getId()));
        } catch (Exception compensationException) {
            logger.error("Compensating MiniBank void-hold failed for booking {} holdId={}: {}",
                booking.getId(), holdId, compensationException.getMessage(), compensationException);
            originalException.addSuppressed(compensationException);
        }
    }

    private String buildBookingVoidIdempotencyKey(Long bookingId) {
        return "booking-" + bookingId + "-void";
    }

    private String buildBookingCaptureIdempotencyKey(Long bookingId) {
        return "booking-" + bookingId + "-capture";
    }

    private String resolveCaptureProviderRef(Bookings booking, MiniBankCaptureHoldResponse captureResponse) {
        if (captureResponse.providerRef() != null && !captureResponse.providerRef().isBlank()) {
            return captureResponse.providerRef();
        }

        if (captureResponse.paymentId() != null && captureResponse.holdId() != null) {
            return captureResponse.paymentId() + ":" + captureResponse.holdId();
        }

        if (booking.getHoldId() != null) {
            return booking.getHoldId().toString();
        }

        return booking.getPaymentId() != null ? booking.getPaymentId().toString() : null;
    }

    private void tryVoidMiniBankHoldIfPresent(Bookings booking) {
        if (booking.getHoldId() == null) {
            logger.debug("Booking {} has no holdId, skipping MiniBank void.", booking.getId());
            return;
        }

        try {
            logger.info("Attempting MiniBank void for booking {} with holdId={}", booking.getId(), booking.getHoldId());
            miniBankClient.voidHold(booking.getHoldId(), buildBookingVoidIdempotencyKey(booking.getId()));
            logger.info("MiniBank void succeeded for booking {} with holdId={}", booking.getId(), booking.getHoldId());
        } catch (Exception ex) {
            logger.error("MiniBank void failed for booking {} with holdId={}: {}",
                booking.getId(), booking.getHoldId(), ex.getMessage(), ex);
        }
    }

    // Helper method to calculate booking total
    private int calculateBookingTotal(Bookings booking) {
        // Calculate based on dates and listing price (consistent with controller)
        long days = java.time.temporal.ChronoUnit.DAYS.between(
            booking.getStartAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
            booking.getEndAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate()) + 1;
        int baseAmount = (int) (days * booking.getListing().getPrice24hCents());

        // Add addon costs if any
        // For now, return base amount
        return baseAmount;
    }

    // Create charges breakdown
    private void createChargesBreakdown(Bookings booking, int totalAmount) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(
            booking.getStartAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
            booking.getEndAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate()) + 1;
        int baseAmount = (int) (days * booking.getListing().getPrice24hCents());

        // BASE charge
        Charges baseCharge = new Charges(booking, "BASE", baseAmount);
        baseCharge.setCurrency("VND");
        baseCharge.setNote("Base rental for " + days + " days");
        chargesRepository.save(baseCharge);

        // PLATFORM_FEE (10% of base)
        int platformFee = (int) (baseAmount * 0.1);
        Charges platformCharge = new Charges(booking, "PLATFORM_FEE", platformFee);
        platformCharge.setCurrency("VND");
        platformCharge.setNote("Platform fee");
        chargesRepository.save(platformCharge);

        // TAX (0 for demo)
        Charges taxCharge = new Charges(booking, "TAX", 0);
        taxCharge.setCurrency("VND");
        taxCharge.setNote("VAT");
        chargesRepository.save(taxCharge);

        // EXTRA charges (addons) - placeholder
        Charges extraCharge = new Charges(booking, "EXTRA", 0);
        extraCharge.setCurrency("VND");
        extraCharge.setNote("Additional services");
        chargesRepository.save(extraCharge);
    }

    // 5. Confirm Booking (Host action)
    @Transactional
    public void confirmBooking(Long bookingId, UUID idempotencyKey) {
        logger.info("Booking {} confirmed by host", bookingId);

        // Check idempotency
        if (idempotencyKeysRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            logger.warn("Duplicate booking confirmation request: booking={} token={}", bookingId, idempotencyKey);
            throw new RuntimeException("Duplicate booking confirmation request");
        }

        // Save idempotency key
        idempotencyKeysRepository.save(new IdempotencyKeys(idempotencyKey));

        Bookings booking = bookingsRepository.findById(bookingId)
            .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (!"PENDING_HOST".equals(booking.getStatus()) && !"PAYMENT_AUTHORIZED".equals(booking.getStatus())) {
            logger.warn("Invalid status for confirmation: booking={} status={}", bookingId, booking.getStatus());
            throw new RuntimeException("Invalid status for confirmation - booking must be PENDING_HOST or PAYMENT_AUTHORIZED");
        }

        // Convert HOLD to BOOKED in calendar
        List<AvailabilityCalendar> calendars = availabilityCalendarRepository
            .findByListingIdAndDateRange(booking.getListing().getId(),
                booking.getStartAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
                booking.getEndAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate());

        for (AvailabilityCalendar cal : calendars) {
            if ("HOLD".equals(cal.getStatus()) && booking.getHoldToken().equals(cal.getHoldToken())) {
                cal.setStatus("BOOKED");
                cal.setHoldToken(null);
                cal.setHoldExpireAt(null);
                availabilityCalendarRepository.save(cal);
            }
        }

        // Update booking status to PAYMENT_AUTHORIZED after host approval
        booking.setStatus("PAYMENT_AUTHORIZED");
        booking.setUpdatedAt(Instant.now());
        bookingsRepository.save(booking);

        logger.info("Booking {} confirmed by host (user {}), status updated to PAYMENT_AUTHORIZED",
            bookingId, booking.getListing().getVehicle().getOwner().getId());

        // Publish outbox event
        outboxEventsRepository.save(new OutboxEvents("Booking", bookingId,
            "BOOKING_CONFIRMED", "{\"bookingId\": " + bookingId + ", \"confirmedBy\": \"HOST\"}"));
    }

    // 6. Reject Booking (Host action)
    @Transactional
    public void rejectBooking(Long bookingId, UUID idempotencyKey) {
        logger.info("Booking {} rejected by host", bookingId);

        // Check idempotency
        if (idempotencyKeysRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            logger.warn("Duplicate booking rejection request: booking={} token={}", bookingId, idempotencyKey);
            throw new RuntimeException("Duplicate booking rejection request");
        }

        // Save idempotency key
        idempotencyKeysRepository.save(new IdempotencyKeys(idempotencyKey));

        Bookings booking = bookingsRepository.findById(bookingId)
            .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (!"PENDING_HOST".equals(booking.getStatus())) {
            logger.warn("Invalid status for rejection: booking={} status={}", bookingId, booking.getStatus());
            throw new RuntimeException("Invalid status for rejection");
        }

        // Convert HOLD back to FREE in calendar
        List<AvailabilityCalendar> calendars = availabilityCalendarRepository
            .findByListingIdAndDateRange(booking.getListing().getId(),
                booking.getStartAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
                booking.getEndAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate());

        for (AvailabilityCalendar cal : calendars) {
            if ("HOLD".equals(cal.getStatus()) && booking.getHoldToken().equals(cal.getHoldToken())) {
                cal.setStatus("FREE");
                cal.setHoldToken(null);
                cal.setHoldExpireAt(null);
                availabilityCalendarRepository.save(cal);
            }
        }

        booking.setStatus("CANCELLED_HOST");
        booking.setUpdatedAt(Instant.now());
        bookingsRepository.save(booking);

        // Canonical runtime truth for pre-capture host rejection:
        // Car Rental releases business state locally, then asks MiniBank to void the hold.
        // Post-capture refund expansion remains deferred to a later phase.
        tryVoidMiniBankHoldIfPresent(booking);

        logger.info("Booking {} cancelled by host (user {})",
            bookingId, booking.getListing().getVehicle().getOwner().getId());

        // Publish outbox event
        outboxEventsRepository.save(new OutboxEvents("Booking", bookingId,
            "BOOKING_REJECTED", "{\"bookingId\": " + bookingId + ", \"rejectedBy\": \"HOST\"}"));
    }

    // 6. Start Trip (Check-in)
    @Transactional
    public void startTrip(Long bookingId, UUID idempotencyKey) {
        logger.info("Trip started for booking {}", bookingId);

        // Check idempotency
        if (idempotencyKeysRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            logger.warn("Duplicate check-in request: booking={} token={}", bookingId, idempotencyKey);
            throw new RuntimeException("Duplicate check-in request");
        }

        // Save idempotency key
        idempotencyKeysRepository.save(new IdempotencyKeys(idempotencyKey));

        Bookings booking = bookingsRepository.findById(bookingId)
            .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (!"PAYMENT_AUTHORIZED".equals(booking.getStatus())) {
            logger.warn("Invalid status for check-in: booking={} status={}", bookingId, booking.getStatus());
            throw new RuntimeException("Invalid status for check-in - booking must be PAYMENT_AUTHORIZED");
        }

        booking.setStatus("IN_PROGRESS");
        booking.setUpdatedAt(Instant.now());
        bookingsRepository.save(booking);

        logger.info("Booking {} status updated to IN_PROGRESS", bookingId);

        // Publish outbox event
        outboxEventsRepository.save(new OutboxEvents("Booking", bookingId,
            "TRIP_STARTED", "{\"bookingId\": " + bookingId + ", \"startedAt\": \"" + Instant.now() + "\"}"));
    }

    // 7. Complete Trip (Check-out)
    @Transactional
    public void completeTrip(Long bookingId, UUID idempotencyKey) {
        logger.info("Trip completed for booking {}", bookingId);

        Bookings booking = bookingsRepository.findById(bookingId)
            .orElseThrow(() -> new RuntimeException("Booking not found"));

        boolean duplicateRequest = idempotencyKeysRepository.findByIdempotencyKey(idempotencyKey).isPresent();
        if (duplicateRequest) {
            logger.warn("Duplicate check-out request detected: booking={} token={}", bookingId, idempotencyKey);
            if ("COMPLETED".equals(booking.getStatus())) {
                logger.info("Booking {} already completed, returning idempotently", bookingId);
                return;
            }
            logger.info("Continuing duplicate check-out request for booking {} because MiniBank capture is replay-safe", bookingId);
        } else {
            idempotencyKeysRepository.save(new IdempotencyKeys(idempotencyKey));
        }

        if ("COMPLETED".equals(booking.getStatus())) {
            logger.info("Booking {} already completed, returning idempotently", bookingId);
            return;
        }

        if (!"IN_PROGRESS".equals(booking.getStatus())) {
            logger.warn("Invalid status for check-out: booking={} status={}", bookingId, booking.getStatus());
            throw new RuntimeException("Invalid status for check-out - booking must be IN_PROGRESS");
        }

        // Create CAPTURE payment
        List<Payments> authPayments = paymentsRepository.findByBookingIdAndTypeAndStatus(booking.getId(), "AUTH", "SUCCEEDED");
        if (authPayments.isEmpty()) {
            logger.error("No successful authorization found for capture: booking={}", bookingId);
            throw new RuntimeException("No successful authorization found for capture");
        }

        List<Payments> capturePayments = paymentsRepository.findByBookingIdAndTypeAndStatus(booking.getId(), "CAPTURE", "SUCCEEDED");
        Payments capturePayment;
        if (!capturePayments.isEmpty()) {
            logger.info("Booking {} already has successful local CAPTURE row, finalizing completion idempotently", bookingId);
            capturePayment = capturePayments.get(0);
        } else {
            if (booking.getHoldId() == null) {
                logger.error("Booking {} has no MiniBank holdId, cannot capture", bookingId);
                throw new RuntimeException("MiniBank hold information is missing for capture");
            }

            final MiniBankCaptureHoldResponse captureResponse;
            try {
                captureResponse = miniBankClient.captureHold(
                    booking.getHoldId(),
                    buildBookingCaptureIdempotencyKey(booking.getId()));
            } catch (MiniBankException ex) {
                logger.error("MiniBank capture failed for booking {} holdId={} with code {}: {}",
                    booking.getId(), booking.getHoldId(), ex.getErrorCode(), ex.getMessage(), ex);
                throw new RuntimeException("MiniBank capture failed. Booking remains IN_PROGRESS and can be retried.", ex);
            }

            try {
                capturePayment = new Payments(booking, "CAPTURE", Math.toIntExact(captureResponse.capturedAmountMinor()), "MINIBANK");
                capturePayment.setProviderRef(resolveCaptureProviderRef(booking, captureResponse));
                capturePayment.setStatus("SUCCEEDED");
                capturePayment.setCreatedAt(Instant.now());
                capturePayment = paymentsRepository.save(capturePayment);
            } catch (RuntimeException ex) {
                logger.error("MiniBank capture succeeded remotely for booking {} but local CAPTURE payment persistence failed. holdId={} paymentId={}",
                    booking.getId(), booking.getHoldId(), booking.getPaymentId(), ex);
                throw ex;
            }

            logger.info("MiniBank CAPTURE succeeded for booking {}: capturedAmount={} paymentId={} holdId={} providerRef={}",
                bookingId,
                captureResponse.capturedAmountMinor(),
                captureResponse.paymentId(),
                captureResponse.holdId(),
                capturePayment.getProviderRef());
        }

        int captureAmount = capturePayment.getAmountCents();

        try {
            createPayoutForHost(booking);

            booking.setStatus("COMPLETED");
            booking.setUpdatedAt(Instant.now());
            bookingsRepository.save(booking);
        } catch (RuntimeException ex) {
            logger.error("MiniBank capture succeeded remotely for booking {} but local booking completion update failed.", bookingId, ex);
            throw ex;
        }

        logger.info("Booking {} status updated to COMPLETED", bookingId);

        // Publish outbox events
        outboxEventsRepository.save(new OutboxEvents("Payment", capturePayment.getId(),
            "PAYMENT_CAPTURED", "{\"bookingId\": " + bookingId + ", \"amount\": " + captureAmount + "}"));

        outboxEventsRepository.save(new OutboxEvents("Booking", bookingId,
            "BOOKING_COMPLETED", "{\"bookingId\": " + bookingId + ", \"completedAt\": \"" + Instant.now() + "\"}"));
    }

    // Create payout for host after successful capture
    private void createPayoutForHost(Bookings booking) {
        List<Payouts> existingPendingPayouts = payoutsRepository.findByBookingIdAndStatus(booking.getId(), "PENDING");
        if (!existingPendingPayouts.isEmpty()) {
            logger.info("Pending payout already exists for booking {}, skipping duplicate payout creation", booking.getId());
            return;
        }

        // Calculate payout amount: base + extra - platform_fee - tax
        List<Charges> charges = chargesRepository.findByBookingId(booking.getId());

        int baseAmount = 0;
        int extraAmount = 0;
        int platformFee = 0;
        int taxAmount = 0;

        for (Charges charge : charges) {
            switch (charge.getLineType()) {
                case "BASE":
                    baseAmount = charge.getAmountCents();
                    break;
                case "EXTRA":
                    extraAmount = charge.getAmountCents();
                    break;
                case "PLATFORM_FEE":
                    platformFee = charge.getAmountCents();
                    break;
                case "TAX":
                    taxAmount = charge.getAmountCents();
                    break;
            }
        }

        int payoutAmount = baseAmount + extraAmount - platformFee - taxAmount;

        if (payoutAmount > 0) {
            Payouts payout = new Payouts(booking.getListing().getVehicle().getOwner(), booking, payoutAmount);
            payout.setCurrency("VND");
            payout.setStatus("PENDING");
            payout.setCreatedAt(Instant.now());

            payoutsRepository.save(payout);
        }
    }

    // 8. Cancel Booking
    @Transactional
    public void cancelBooking(Long bookingId, UUID idempotencyKey, boolean isHostCancellation) {
        logger.info("Booking {} cancellation requested by {}", bookingId, isHostCancellation ? "HOST" : "GUEST");

        // Check idempotency
        if (idempotencyKeysRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            logger.warn("Duplicate cancellation request: booking={} token={}", bookingId, idempotencyKey);
            throw new RuntimeException("Duplicate cancellation request");
        }

        // Save idempotency key
        IdempotencyKeys key = new IdempotencyKeys(idempotencyKey);
        idempotencyKeysRepository.save(key);

        Bookings booking = bookingsRepository.findById(bookingId)
            .orElseThrow(() -> new RuntimeException("Booking not found"));

        // Check if already cancelled
        if (booking.getStatus().startsWith("CANCELLED")) {
            tryVoidMiniBankHoldIfPresent(booking);
            logger.info("Booking {} already cancelled, returning idempotently", bookingId);
            return; // Idempotent - already cancelled
        }

        // Validate cancellation rules
        if ("COMPLETED".equals(booking.getStatus())) {
            logger.warn("Cannot cancel completed booking: booking={}", bookingId);
            throw new RuntimeException("Cannot cancel completed booking");
        }

        if ("IN_PROGRESS".equals(booking.getStatus())) {
            logger.warn("Cannot cancel booking in progress: booking={}", bookingId);
            throw new RuntimeException("Cannot cancel booking in progress");
        }

        // Boundary note:
        // - pre-capture cancellation/rejection canonical runtime truth = MiniBank void hold
        // - post-capture refund remains a deferred/non-canonical MiniBank runtime path for now
        int refundAmount = handleCancellationRefund(booking, isHostCancellation);

        // Release calendar dates
        releaseCalendarDates(booking);

        // Cancel any pending payouts
        cancelPendingPayouts(booking);

        // Update booking status
        String cancelStatus = isHostCancellation ? "CANCELLED_HOST" : "CANCELLED_GUEST";
        booking.setStatus(cancelStatus);
        booking.setUpdatedAt(Instant.now());
        bookingsRepository.save(booking);

        tryVoidMiniBankHoldIfPresent(booking);

        logger.info("Booking {} cancelled by {} with refund amount {}",
            bookingId, isHostCancellation ? "HOST" : "GUEST", refundAmount);

        // Publish cancellation event
        outboxEventsRepository.save(new OutboxEvents("Booking", bookingId,
            "BOOKING_CANCELLED", "{\"bookingId\": " + bookingId + ", \"cancelledBy\": \"" +
            (isHostCancellation ? "HOST" : "GUEST") + "\", \"refundAmount\": " + refundAmount + "}"));
    }

    // Transitional refund boundary:
    // - before capture, runtime truth is to void the MiniBank hold and release funds safely
    // - after capture, refund/correction is not yet the canonical MiniBank runtime path in this repo
    //   and remains a deferred placeholder/business approximation until a dedicated refund phase lands
    private int handleCancellationRefund(Bookings booking, boolean isHostCancellation) {
        List<Payments> authPayments = paymentsRepository.findByBookingIdAndTypeAndStatus(booking.getId(), "AUTH", "SUCCEEDED");
        List<Payments> capturePayments = paymentsRepository.findByBookingIdAndTypeAndStatus(booking.getId(), "CAPTURE", "SUCCEEDED");

        if (!authPayments.isEmpty()) {
            Payments authPayment = authPayments.get(0);

            if (capturePayments.isEmpty()) {
                // Legacy/local payment row handling is kept for compatibility,
                // but the canonical external financial side effect for this pre-capture state
                // is the MiniBank void triggered by tryVoidMiniBankHoldIfPresent().
                Payments voidPayment = paymentProvider.voidAuthorization(booking, authPayment);
                paymentsRepository.save(voidPayment);
                return authPayment.getAmountCents(); // Full amount voided
            } else {
                logger.warn("Booking {} entered deferred post-capture refund placeholder path. Canonical MiniBank refund flow is not implemented yet.", booking.getId());
                // CAPTURE exists - calculate refund based on policy
                int refundAmount = calculateRefundAmount(booking, isHostCancellation);
                if (refundAmount > 0) {
                    Payments refundPayment = paymentProvider.refund(booking, authPayment, refundAmount);
                    paymentsRepository.save(refundPayment);
                }
                return refundAmount;
            }
        }
        return 0; // No payments to refund
    }

    // Calculate refund amount based on policy and timing
    private int calculateRefundAmount(Bookings booking, boolean isHostCancellation) {
        if (isHostCancellation) {
            // Host cancellation - always full refund
            return calculateBookingTotal(booking);
        }

        // Guest cancellation - check policy and timing
        String policy = booking.getListing().getCancellationPolicy().toString();
        
        long hoursUntilStart = java.time.Duration.between(Instant.now(), booking.getStartAt()).toHours();

        switch (policy) {
            case "STRICT":
                return 0; // No refund
            case "MODERATE":
                if (hoursUntilStart > 24) {
                    return calculateBookingTotal(booking); // Full refund
                } else {
                    return calculateBookingTotal(booking) / 2; // 50% refund
                }
            case "FLEXIBLE":
            default:
                return calculateBookingTotal(booking); // Full refund
        }
    }

    // Release calendar dates back to FREE
    private void releaseCalendarDates(Bookings booking) {
        List<AvailabilityCalendar> calendars = availabilityCalendarRepository
            .findByListingIdAndDateRange(booking.getListing().getId(),
                booking.getStartAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
                booking.getEndAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate());

        for (AvailabilityCalendar cal : calendars) {
            cal.setStatus("FREE");
            cal.setHoldToken(null);
            cal.setHoldExpireAt(null);
            availabilityCalendarRepository.save(cal);
        }
    }

    // Cancel any pending payouts
    private void cancelPendingPayouts(Bookings booking) {
        // Find and cancel any pending payouts for this booking
        List<Payouts> pendingPayouts = payoutsRepository.findByBookingIdAndStatus(booking.getId(), "PENDING");
        for (Payouts payout : pendingPayouts) {
            payout.setStatus("CANCELLED");
            payoutsRepository.save(payout);
        }
    }
    @Deprecated(forRemoval = false)
    @Transactional
    public void approveBooking(Long bookingId, UUID idempotencyKey) {
        logger.warn("approveBooking() is a transitional wrapper. Delegating booking {} to canonical confirmBooking() path.", bookingId);
        confirmBooking(bookingId, idempotencyKey);
    }

    // Extended Check-in with inspection data
    @Transactional
    public void hostCheckIn(Long bookingId, Integer odometerKm, Byte fuelLevelPct,
                           String photosJson, String notes, UUID idempotencyKey) {
        logger.info("Host check-in for booking {}: odometer={}, fuel={}, photos={}",
            bookingId, odometerKm, fuelLevelPct, photosJson);

        // Check idempotency
        if (idempotencyKeysRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            logger.warn("Duplicate host check-in request: booking={} token={}", bookingId, idempotencyKey);
            throw new RuntimeException("Duplicate check-in request");
        }
        idempotencyKeysRepository.save(new IdempotencyKeys(idempotencyKey));

        Bookings booking = bookingsRepository.findById(bookingId)
            .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (!"PAYMENT_AUTHORIZED".equals(booking.getStatus())) {
            throw new RuntimeException("Invalid status for check-in - booking must be PAYMENT_AUTHORIZED");
        }

        // Keep photosJson as null when no photos are provided - database constraint allows NULL
        String safePhotosJson = photosJson;

        // Create trip inspection record
        TripInspections inspection = new TripInspections(booking, "CHECKIN");
        inspection.setOdometerKm(odometerKm);
        inspection.setFuelLevelPct(fuelLevelPct);
        inspection.setPhotosJson(safePhotosJson);
        inspection.setNotes(notes);
        tripInspectionsRepository.save(inspection);

        // Publish event for host check-in
        outboxEventsRepository.save(new OutboxEvents("Booking", bookingId,
            "CHECKIN_HOST", "{\"bookingId\": " + bookingId + ", \"odometerKm\": " + odometerKm +
            ", \"fuelLevelPct\": " + fuelLevelPct + ", \"inspectionId\": " + inspection.getId() + "}"));

        logger.info("Host check-in completed for booking {}, inspection ID: {}", bookingId, inspection.getId());
    }

    // Guest acknowledgment of check-in
    @Transactional
    public void guestAcknowledgeCheckIn(Long bookingId, UUID idempotencyKey) {
        logger.info("Guest acknowledgment for booking {}", bookingId);

        // Check idempotency
        if (idempotencyKeysRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            logger.warn("Duplicate guest acknowledgment: booking={} token={}", bookingId, idempotencyKey);
            throw new RuntimeException("Duplicate acknowledgment");
        }
        idempotencyKeysRepository.save(new IdempotencyKeys(idempotencyKey));

        Bookings booking = bookingsRepository.findById(bookingId)
            .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (!"PAYMENT_AUTHORIZED".equals(booking.getStatus())) {
            throw new RuntimeException("Invalid status for acknowledgment");
        }

        // Check if host has already checked in
        List<OutboxEvents> hostCheckInEvents = outboxEventsRepository
            .findByAggregateTypeAndAggregateIdAndEventType("Booking", bookingId, "CHECKIN_HOST");

        if (hostCheckInEvents.isEmpty()) {
            throw new RuntimeException("Host must check-in first");
        }

        // Publish guest acknowledgment event
        outboxEventsRepository.save(new OutboxEvents("Booking", bookingId,
            "CHECKIN_GUEST_ACK", "{\"bookingId\": " + bookingId + ", \"acknowledgedAt\": \"" + Instant.now() + "\"}"));

        // Check if both events exist - if so, transition to IN_PROGRESS
        List<OutboxEvents> guestAckEvents = outboxEventsRepository
            .findByAggregateTypeAndAggregateIdAndEventType("Booking", bookingId, "CHECKIN_GUEST_ACK");

        if (!hostCheckInEvents.isEmpty() && !guestAckEvents.isEmpty()) {
            booking.setStatus("IN_PROGRESS");
            booking.setUpdatedAt(Instant.now());
            bookingsRepository.save(booking);

            outboxEventsRepository.save(new OutboxEvents("Booking", bookingId,
                "TRIP_STARTED", "{\"bookingId\": " + bookingId + ", \"startedAt\": \"" + Instant.now() + "\"}"));

            logger.info("Trip started for booking {} after dual confirmation", bookingId);
        }
    }

    // Extended Check-out with charge calculation
    @Transactional
    public void hostCheckOut(Long bookingId, Integer odometerKm, Byte fuelLevelPct,
                            String photosJson, String notes, boolean needsCleaning,
                            UUID idempotencyKey) {
        logger.info("Host check-out for booking {}: odometer={}, fuel={}, cleaning={}",
            bookingId, odometerKm, fuelLevelPct, needsCleaning);

        // Check idempotency
        if (idempotencyKeysRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            logger.warn("Duplicate check-out request: booking={} token={}", bookingId, idempotencyKey);
            throw new RuntimeException("Duplicate check-out request");
        }
        idempotencyKeysRepository.save(new IdempotencyKeys(idempotencyKey));

        Bookings booking = bookingsRepository.findById(bookingId)
            .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (!"IN_PROGRESS".equals(booking.getStatus())) {
            throw new RuntimeException("Invalid status for check-out - booking must be IN_PROGRESS");
        }

        // Create checkout inspection record
        TripInspections inspection = new TripInspections(booking, "CHECKOUT");
        inspection.setOdometerKm(odometerKm);
        inspection.setFuelLevelPct(fuelLevelPct);
        inspection.setPhotosJson(photosJson);
        inspection.setNotes(notes);
        tripInspectionsRepository.save(inspection);

        // Calculate additional charges
        calculateAdditionalCharges(booking, needsCleaning);

        // Transitional path: preserve host checkout endpoint while delegating
        // booking completion and MiniBank capture to the canonical completion seam.
        completeTripWithCharges(bookingId, idempotencyKey);

        logger.info("Host check-out completed for booking {}, inspection ID: {}", bookingId, inspection.getId());
    }

    // Calculate additional charges based on trip inspections
    private void calculateAdditionalCharges(Bookings booking, boolean needsCleaning) {
        List<TripInspections> inspections = tripInspectionsRepository.findByBooking_Id(booking.getId());

        TripInspections checkIn = null;
        TripInspections checkOut = null;

        for (TripInspections insp : inspections) {
            if ("CHECKIN".equals(insp.getChkType())) {
                checkIn = insp;
            } else if ("CHECKOUT".equals(insp.getChkType())) {
                checkOut = insp;
            }
        }

        if (checkIn != null && checkOut != null) {
            // Calculate KM_OVER - Đồng nhất logic với BookingWebController
            if (checkIn.getOdometerKm() != null && checkOut.getOdometerKm() != null) {
                int kmUsed = checkOut.getOdometerKm() - checkIn.getOdometerKm();
                long rentalDays = java.time.temporal.ChronoUnit.DAYS.between(
                    booking.getStartAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
                    booking.getEndAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate()) + 1;

                int kmLimit = booking.getListing().getKmLimit24h() * (int) rentalDays;
                int kmOver = Math.max(0, kmUsed - kmLimit);

                if (kmOver > 0) {
                    // Assume 5000 VND per extra km
                    int kmOverCharge = kmOver * 5000;
                    Charges kmCharge = new Charges(booking, "KM_OVER", kmOverCharge);
                    kmCharge.setCurrency("VND");
                    kmCharge.setNote("Extra " + kmOver + " km at 5000 VND/km");
                    chargesRepository.save(kmCharge);
                }
            }

            // Calculate FUEL charge
            if (checkIn.getFuelLevelPct() != null && checkOut.getFuelLevelPct() != null) {
                int fuelUsed = checkIn.getFuelLevelPct() - checkOut.getFuelLevelPct();
                if (fuelUsed > 0) {
                    // Assume 25,000 VND per liter, average car holds 50 liters
                    int fuelCharge = fuelUsed * 25000;
                    Charges fuelChargeEntity = new Charges(booking, "FUEL", fuelCharge);
                    fuelChargeEntity.setCurrency("VND");
                    fuelChargeEntity.setNote("Fuel shortage: " + fuelUsed + "%");
                    chargesRepository.save(fuelChargeEntity);
                }
            }
        }

        // Calculate CLEANING charge
        if (needsCleaning) {
            Charges cleaningCharge = new Charges(booking, "CLEANING", 50000); // 50,000 VND fixed
            cleaningCharge.setCurrency("VND");
            cleaningCharge.setNote("Car cleaning fee");
            chargesRepository.save(cleaningCharge);
        }
    }

    @Deprecated(forRemoval = false)
    // Transitional wrapper kept for the host checkout flow.
    // Canonical completion/capture behavior remains in completeTrip().
    private void completeTripWithCharges(Long bookingId, UUID idempotencyKey) {
        logger.info("completeTripWithCharges() is a transitional wrapper for booking {}. Delegating to canonical completeTrip() seam.", bookingId);
        completeTrip(bookingId, idempotencyKey);
    }

    // Calculate outstanding amount for a booking (for surcharge payment)
    public int calculateOutstanding(Long bookingId) {
        Bookings booking = bookingsRepository.findById(bookingId)
            .orElseThrow(() -> new RuntimeException("Booking not found"));

        // Only calculate outstanding for COMPLETED bookings
        if (!"COMPLETED".equals(booking.getStatus())) {
            return 0;
        }

        // Get all charges for this booking
        List<Charges> allCharges = chargesRepository.findByBookingId(bookingId);

        // Separate initial booking charges from surcharges
        int initialBookingCharges = 0; // BASE + PLATFORM_FEE + TAX + EXTRA
        int surchargeCharges = 0; // FUEL + KM_OVER + CLEANING

        for (Charges charge : allCharges) {
            switch (charge.getLineType()) {
                case "BASE":
                case "PLATFORM_FEE":
                case "TAX":
                case "EXTRA":
                    initialBookingCharges += charge.getAmountCents();
                    break;
                case "FUEL":
                case "KM_OVER":
                case "CLEANING":
                    surchargeCharges += charge.getAmountCents();
                    break;
            }
        }

        // Total payments (successful AUTH + CAPTURE - REFUND)
        List<Payments> payments = paymentsRepository.findByBookingId(bookingId);
        int totalPaid = payments.stream()
            .filter(p -> "SUCCEEDED".equals(p.getStatus()))
            .mapToInt(p -> {
                if ("AUTH".equals(p.getType()) || "CAPTURE".equals(p.getType())) {
                    return p.getAmountCents();
                } else if ("REFUND".equals(p.getType())) {
                    return -p.getAmountCents(); // Subtract refunds
                }
                return 0;
            })
            .sum();

        // Outstanding = surcharge charges - (total paid - initial booking charges)
        // This represents surcharges that exceed the initial booking payment
        int outstanding = surchargeCharges - (totalPaid - initialBookingCharges);
        return Math.max(0, outstanding); // Never negative
    }

    // Deferred/local placeholder path:
    // surcharge collection here is not yet the canonical MiniBank financial-authority runtime path.
    // It is intentionally kept local until a later surcharge/payment expansion phase.
    @Transactional
    public void payOutstandingSurcharge(Long bookingId, User customer, UUID idempotencyKey) {
        logger.info("Processing surcharge payment for booking {} by customer {}", bookingId, customer.getId());

        // Check idempotency
        if (idempotencyKeysRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            logger.warn("Duplicate surcharge payment request: booking={} token={}", bookingId, idempotencyKey);
            throw new RuntimeException("Duplicate surcharge payment request");
        }
        idempotencyKeysRepository.save(new IdempotencyKeys(idempotencyKey));

        Bookings booking = bookingsRepository.findById(bookingId)
            .orElseThrow(() -> new RuntimeException("Booking not found"));

        // Verify customer ownership
        if (!booking.getGuest().getId().equals(customer.getId())) {
            throw new RuntimeException("Access denied - not the booking guest");
        }

        // Check if booking is completed (has surcharges)
        if (!"COMPLETED".equals(booking.getStatus())) {
            throw new RuntimeException("Booking must be completed to pay surcharges");
        }

        int outstanding = calculateOutstanding(bookingId);
        if (outstanding <= 0) {
            logger.info("No outstanding amount for booking {}", bookingId);
            return;
        }

        logger.warn("Booking {} is using deferred/local surcharge payment placeholder logic. MiniBank surcharge runtime integration is not canonical yet.", bookingId);

        // Create additional payment (mô phỏng thanh toán surcharge)
        Payments surchargePayment = new Payments();
        surchargePayment.setBooking(booking);
        surchargePayment.setType("CAPTURE"); // Hoặc "SURCHARGE_PAYMENT"
        surchargePayment.setAmountCents(outstanding);
        surchargePayment.setCurrency("VND");
        surchargePayment.setProvider("MOCK_SURCHARGE");
        surchargePayment.setProviderRef("surcharge_" + bookingId + "_" + System.currentTimeMillis());
        surchargePayment.setStatus("SUCCEEDED");
        surchargePayment.setCreatedAt(Instant.now());
        paymentsRepository.save(surchargePayment);

        // Create additional payout for host (full surcharge amount)
        Payouts surchargePayout = new Payouts();
        surchargePayout.setOwner(booking.getListing().getVehicle().getOwner());
        surchargePayout.setBooking(booking);
        surchargePayout.setAmountCents(outstanding);
        surchargePayout.setCurrency("VND");
        surchargePayout.setBankRef("surcharge_payout_" + bookingId);
        surchargePayout.setStatus("PENDING");
        surchargePayout.setCreatedAt(Instant.now());
        payoutsRepository.save(surchargePayout);

        logger.info("Surcharge payment processed: booking={}, amount={}, paymentId={}, payoutId={}",
            bookingId, outstanding, surchargePayment.getId(), surchargePayout.getId());

        // Publish event
        outboxEventsRepository.save(new OutboxEvents("Booking", bookingId,
            "SURCHARGE_PAID", "{\"bookingId\": " + bookingId + ", \"amount\": " + outstanding +
            ", \"paymentId\": " + surchargePayment.getId() + ", \"payoutId\": " + surchargePayout.getId() + "}"));
    }



    // Helper method to find events (add to OutboxEventsRepository if needed)
    public List<OutboxEvents> findEvents(String aggregateType, Long aggregateId, String eventType) {
        // This would need to be implemented in the repository
        return outboxEventsRepository.findAll().stream()
            .filter(e -> aggregateType.equals(e.getAggregateType()) &&
                        aggregateId.equals(e.getAggregateId()) &&
                        eventType.equals(e.getEventType()))
            .toList();
    }

    // Check for NO_SHOW and automatically update status
    @Transactional
    public void checkAndUpdateNoShowBookings() {
        logger.info("Checking for NO_SHOW bookings...");

        Instant now = Instant.now();
        Instant cutoffTime = now.minus(java.time.Duration.ofHours(2)); // 2 hours after start time

        // Find bookings that should have started but haven't been checked in
        List<Bookings> potentialNoShows = bookingsRepository.findByStatusAndStartAtBefore("PAYMENT_AUTHORIZED", cutoffTime);

        for (Bookings booking : potentialNoShows) {
            // Double-check: ensure no check-in events exist
            List<OutboxEvents> checkInEvents = outboxEventsRepository
                .findByAggregateTypeAndAggregateIdAndEventType("Booking", booking.getId(), "TRIP_STARTED");

            if (checkInEvents.isEmpty()) {
                // No check-in found - mark as NO_SHOW
                booking.setStatus("NO_SHOW_GUEST");
                booking.setUpdatedAt(now);
                bookingsRepository.save(booking);

                // Handle refund for no-show
                handleNoShowRefund(booking);

                outboxEventsRepository.save(new OutboxEvents("Booking", booking.getId(),
                    "BOOKING_NO_SHOW", "{\"bookingId\": " + booking.getId() + ", \"reason\": \"NO_SHOW_GUEST\"}"));

                logger.info("Booking {} marked as NO_SHOW_GUEST due to no check-in", booking.getId());
            }
        }
    }

    // Handle refund for no-show bookings
    private void handleNoShowRefund(Bookings booking) {
        List<Payments> authPayments = paymentsRepository.findByBookingIdAndTypeAndStatus(booking.getId(), "AUTH", "SUCCEEDED");

        if (!authPayments.isEmpty()) {
            Payments authPayment = authPayments.get(0);

            // For no-show, apply partial refund (e.g., 50% retention as penalty)
            int refundAmount = authPayment.getAmountCents() / 2; // 50% refund

            if (refundAmount > 0) {
                Payments refundPayment = paymentProvider.refund(booking, authPayment, refundAmount);
                paymentsRepository.save(refundPayment);
                logger.info("Processed {} VND refund for no-show booking {}", refundAmount, booking.getId());
            }
        }
    }
}
