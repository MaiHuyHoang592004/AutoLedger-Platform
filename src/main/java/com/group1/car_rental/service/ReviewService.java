package com.group1.car_rental.service;

import com.group1.car_rental.dto.ReviewCreateRequest;
import com.group1.car_rental.dto.ReviewResponse;
import com.group1.car_rental.entity.*;
import com.group1.car_rental.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReviewService {

    private static final Logger logger = LoggerFactory.getLogger(ReviewService.class);

    @Autowired
    private ReviewsRepository reviewsRepository;

    @Autowired
    private BookingsRepository bookingsRepository;

    @Autowired
    private CarsRepository carsRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IdempotencyKeysRepository idempotencyKeysRepository;

    @Transactional
    public ReviewResponse createReview(Long bookingId, Long userId, ReviewCreateRequest request, String idempotencyKey) {
        logger.info("Creating review: booking={}, user={}, rating={}", bookingId, userId, request.getRating());

        // Check idempotency
        if (idempotencyKeysRepository.findByIdempotencyKey(java.util.UUID.fromString(idempotencyKey)).isPresent()) {
            throw new RuntimeException("Duplicate review submission");
        }
        idempotencyKeysRepository.save(new IdempotencyKeys(java.util.UUID.fromString(idempotencyKey)));

        // Validate booking exists and belongs to user
        Bookings booking = bookingsRepository.findById(bookingId)
            .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (!booking.getGuest().getId().equals(userId)) {
            throw new RuntimeException("You can only review your own bookings");
        }

        // Check booking status
        if (!"COMPLETED".equals(booking.getStatus())) {
            throw new RuntimeException("Can only review completed bookings");
        }

        // Check if review already exists for this booking
        if (reviewsRepository.existsByBooking(booking)) {
            throw new RuntimeException("Review already exists for this booking");
        }

        // Get user
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        // Get the host (toUser) - owner of the vehicle
        User toUser = booking.getListing().getVehicle().getOwner();

        // Create review
        Reviews review = new Reviews(booking, user, toUser, request.getRating());
        review.setComment(request.getComment());
        Reviews savedReview = reviewsRepository.save(review);

        // Update car rating
        updateCarRating(booking.getListing().getVehicle().getId());

        // Convert to response
        ReviewResponse response = new ReviewResponse();
        response.setId(savedReview.getId());
        response.setReviewerName(user.getEmail()); // Or get from profile if available
        response.setRating(savedReview.getRating());
        response.setComment(savedReview.getComment());
        response.setCreatedAt(savedReview.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDateTime());

        logger.info("Review created successfully: {}", savedReview.getId());
        return response;
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> getReviewsByListing(Long listingId) {
        List<Reviews> reviews = reviewsRepository.findAllByBooking_Listing_Id(listingId);

        return reviews.stream()
            .map(review -> {
                ReviewResponse response = new ReviewResponse();
                response.setId(review.getId());
                response.setReviewerName(review.getFromUser().getEmail());
                response.setRating(review.getRating());
                response.setComment(review.getComment());
                response.setCreatedAt(review.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDateTime());
                return response;
            })
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> getMyReviews(Long userId) {
        List<Reviews> reviews = reviewsRepository.findAllByFromUser_Id(userId);

        return reviews.stream()
            .map(review -> {
                ReviewResponse response = new ReviewResponse();
                response.setId(review.getId());
                response.setReviewerName(review.getFromUser().getEmail());
                response.setRating(review.getRating());
                response.setComment(review.getComment());
                response.setCreatedAt(review.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDateTime());
                return response;
            })
            .collect(Collectors.toList());
    }

    @Transactional
    public void deleteReview(Long reviewId, Long userId) {
        Reviews review = reviewsRepository.findById(reviewId)
            .orElseThrow(() -> new RuntimeException("Review not found"));

        if (!review.getFromUser().getId().equals(userId)) {
            throw new RuntimeException("You can only delete your own reviews");
        }

        Long carId = review.getBooking().getListing().getVehicle().getId();
        reviewsRepository.delete(review);

        // Update car rating after deletion
        updateCarRating(carId);

        logger.info("Review deleted: {}", reviewId);
    }

    private void updateCarRating(Long carId) {
        List<Reviews> reviews = reviewsRepository.findAllByBooking_Listing_Vehicle_Id(carId);
        double avgRating = reviews.stream()
            .mapToInt(r -> r.getRating())
            .average()
            .orElse(0.0);

        int numReviews = reviews.size();

        Cars car = carsRepository.findById(carId)
            .orElseThrow(() -> new RuntimeException("Car not found"));

        car.setRating(avgRating);
        car.setNumReviews(numReviews);
        carsRepository.save(car);

        logger.info("Updated car {} rating: {} ({} reviews)", carId, avgRating, numReviews);
    }
}
