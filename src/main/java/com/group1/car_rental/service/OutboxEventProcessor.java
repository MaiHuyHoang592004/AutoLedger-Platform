package com.group1.car_rental.service;

import com.group1.car_rental.entity.OutboxEvents;
import com.group1.car_rental.repository.OutboxEventsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class OutboxEventProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OutboxEventProcessor.class);

    @Autowired
    private OutboxEventsRepository outboxEventsRepository;

    // Process events every 5 seconds
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void processUnprocessedEvents() {
        List<OutboxEvents> unprocessedEvents = outboxEventsRepository.findUnprocessedEvents();

        if (unprocessedEvents.isEmpty()) {
            return; // No events to process
        }

        logger.info("Processing {} unprocessed outbox events", unprocessedEvents.size());

        for (OutboxEvents event : unprocessedEvents) {
            try {
                processEvent(event);
                // Mark as processed
                event.setProcessedAt(Instant.now());
                outboxEventsRepository.save(event);
                logger.debug("Successfully processed event: {} - {}", event.getEventType(), event.getAggregateId());
            } catch (Exception e) {
                logger.error("Failed to process event: {} - {} - Error: {}",
                    event.getEventType(), event.getAggregateId(), e.getMessage(), e);
                // In production, you might want to implement retry logic or dead letter queue
                // For now, we'll leave it unprocessed to retry on next run
            }
        }
    }

    private void processEvent(OutboxEvents event) {
        switch (event.getEventType()) {
            case "BOOKING_CREATED":
                handleBookingCreated(event);
                break;
            case "BOOKING_CONFIRMED":
                handleBookingConfirmed(event);
                break;
            case "BOOKING_REJECTED":
                handleBookingRejected(event);
                break;
            case "BOOKING_CANCELLED":
                handleBookingCancelled(event);
                break;
            case "TRIP_STARTED":
                handleTripStarted(event);
                break;
            case "BOOKING_COMPLETED":
                handleBookingCompleted(event);
                break;
            case "PAYMENT_AUTHORIZED":
                handlePaymentAuthorized(event);
                break;
            case "PAYMENT_CAPTURED":
                handlePaymentCaptured(event);
                break;
            case "HOLDS_EXPIRED":
                handleHoldsExpired(event);
                break;
            default:
                logger.warn("Unknown event type: {}", event.getEventType());
        }
    }

    private void handleBookingCreated(OutboxEvents event) {
        // Send booking confirmation email to guest
        logger.info("📧 Sending booking confirmation email to guest for booking {}", event.getAggregateId());

        // Send notification to host about new booking request
        logger.info("📱 Sending booking request notification to host for booking {}", event.getAggregateId());

        // In production: integrate with email service, push notifications, etc.
        simulateEmailSend("guest@example.com", "Booking Confirmed", "Your booking " + event.getAggregateId() + " has been created");
        simulateNotificationSend("host@example.com", "New Booking Request", "You have a new booking request for your car");
    }

    private void handleBookingConfirmed(OutboxEvents event) {
        // Send confirmation email to guest
        logger.info("📧 Sending booking approval email to guest for booking {}", event.getAggregateId());

        // Send confirmation to host
        logger.info("📱 Sending booking approval confirmation to host for booking {}", event.getAggregateId());

        simulateEmailSend("guest@example.com", "Booking Approved", "Your booking " + event.getAggregateId() + " has been approved by the host");
        simulateNotificationSend("host@example.com", "Booking Approved", "You approved booking " + event.getAggregateId());
    }

    private void handleBookingRejected(OutboxEvents event) {
        // Send rejection email to guest
        logger.info("📧 Sending booking rejection email to guest for booking {}", event.getAggregateId());

        // Send notification to host
        logger.info("📱 Sending booking rejection confirmation to host for booking {}", event.getAggregateId());

        simulateEmailSend("guest@example.com", "Booking Declined", "Your booking " + event.getAggregateId() + " has been declined by the host");
        simulateNotificationSend("host@example.com", "Booking Declined", "You declined booking " + event.getAggregateId());
    }

    private void handleBookingCancelled(OutboxEvents event) {
        // Send cancellation email to both parties
        logger.info("📧 Sending booking cancellation email for booking {}", event.getAggregateId());

        simulateEmailSend("guest@example.com", "Booking Cancelled", "Booking " + event.getAggregateId() + " has been cancelled");
        simulateEmailSend("host@example.com", "Booking Cancelled", "Booking " + event.getAggregateId() + " has been cancelled");
    }

    private void handleTripStarted(OutboxEvents event) {
        // Send trip start confirmation
        logger.info("📱 Sending trip start notification for booking {}", event.getAggregateId());

        simulateNotificationSend("guest@example.com", "Trip Started", "Your trip for booking " + event.getAggregateId() + " has started");
        simulateNotificationSend("host@example.com", "Trip Started", "Trip started for booking " + event.getAggregateId());
    }

    private void handleBookingCompleted(OutboxEvents event) {
        // Send completion confirmation and request review
        logger.info("📧 Sending trip completion email and review request for booking {}", event.getAggregateId());

        simulateEmailSend("guest@example.com", "Trip Completed", "Your trip for booking " + event.getAggregateId() + " has been completed. Please leave a review.");
        simulateEmailSend("host@example.com", "Trip Completed", "Trip completed for booking " + event.getAggregateId() + ". Please leave a review.");
    }

    private void handlePaymentAuthorized(OutboxEvents event) {
        // Send payment confirmation
        logger.info("💳 Sending payment authorization confirmation for booking {}", event.getAggregateId());

        simulateEmailSend("guest@example.com", "Payment Authorized", "Payment has been authorized for booking " + event.getAggregateId());
    }

    private void handlePaymentCaptured(OutboxEvents event) {
        // Send payment capture confirmation
        logger.info("💰 Sending payment capture confirmation for booking {}", event.getAggregateId());

        simulateEmailSend("host@example.com", "Payment Received", "Payment has been captured for booking " + event.getAggregateId());
    }

    private void handleHoldsExpired(OutboxEvents event) {
        // Log hold expiration summary (could trigger alerts if too many)
        logger.info("⏰ Hold expiration summary: {}", event.getPayloadJson());

        // In production: send alert to monitoring system if expiration rate is high
        // simulateAlertSend("admin@example.com", "High Hold Expiration Rate", event.getPayloadJson());
    }

    // Simulation methods - replace with actual service integrations
    private void simulateEmailSend(String to, String subject, String body) {
        logger.info("📧 [SIMULATED EMAIL] To: {} | Subject: {} | Body: {}", to, subject, body);
        // In production: integrate with SendGrid, AWS SES, etc.
    }

    private void simulateNotificationSend(String to, String title, String message) {
        logger.info("📱 [SIMULATED NOTIFICATION] To: {} | Title: {} | Message: {}", to, title, message);
        // In production: integrate with Firebase, APNs, etc.
    }

    private void simulateAlertSend(String to, String subject, String details) {
        logger.warn("🚨 [SIMULATED ALERT] To: {} | Subject: {} | Details: {}", to, subject, details);
        // In production: integrate with PagerDuty, Slack, etc.
    }
}
