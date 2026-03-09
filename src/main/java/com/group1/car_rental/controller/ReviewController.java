package com.group1.car_rental.controller;

import com.group1.car_rental.dto.ReviewCreateRequest;
import com.group1.car_rental.dto.ReviewResponse;
import com.group1.car_rental.entity.User;
import com.group1.car_rental.repository.UserRepository;
import com.group1.car_rental.service.ReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Controller
@RequestMapping("/api/reviews")
public class ReviewController {

    private static final Logger logger = LoggerFactory.getLogger(ReviewController.class);

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private UserRepository userRepository;

    // Helper method to get current authenticated user
    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }
        String email = auth.getName();

        // For now, we'll need to get user from repository
        // In a real app, you might want to store user in session or use custom UserDetails
        throw new RuntimeException("Need to implement user lookup from email: " + email);
    }

    // Helper method to get current user ID
    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }

        // Since we're using the default UserDetails (org.springframework.security.core.userdetails.User)
        // and the username is the email, we need to look up the user ID from the database
        String email = auth.getName();

        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));

        return user.getId();
    }

    // Create review for a booking
    @PostMapping("/{bookingId}")
    public ResponseEntity<?> createReview(
            @PathVariable Long bookingId,
            @Valid @RequestBody ReviewCreateRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        try {
            Long userId = getCurrentUserId();
            logger.info("Creating review - bookingId: {}, userId: {}, rating: {}", bookingId, userId, request.getRating());

            ReviewResponse response = reviewService.createReview(bookingId, userId, request, idempotencyKey);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating review: {}", e.getMessage(), e);

            // Return detailed error message
            return ResponseEntity.badRequest()
                .body(Map.of("error", true, "message", e.getMessage()));
        }
    }

    // Get all reviews for a listing
    @GetMapping("/listing/{listingId}")
    public ResponseEntity<List<ReviewResponse>> getReviewsByListing(@PathVariable Long listingId) {
        try {
            List<ReviewResponse> reviews = reviewService.getReviewsByListing(listingId);
            return ResponseEntity.ok(reviews);
        } catch (Exception e) {
            logger.error("Error getting reviews for listing {}: {}", listingId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Get current user's reviews
    @GetMapping("/mine")
    public ResponseEntity<List<ReviewResponse>> getMyReviews() {
        try {
            Long userId = getCurrentUserId();
            List<ReviewResponse> reviews = reviewService.getMyReviews(userId);
            return ResponseEntity.ok(reviews);
        } catch (Exception e) {
            logger.error("Error getting user reviews: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Delete a review
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<String> deleteReview(@PathVariable Long reviewId) {
        try {
            Long userId = getCurrentUserId();
            reviewService.deleteReview(reviewId, userId);
            return ResponseEntity.ok("Review deleted successfully");
        } catch (Exception e) {
            logger.error("Error deleting review {}: {}", reviewId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
}
