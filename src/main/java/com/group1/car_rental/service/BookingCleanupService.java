package com.group1.car_rental.service;

import com.group1.car_rental.entity.AvailabilityCalendar;
import com.group1.car_rental.repository.AvailabilityCalendarRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BookingCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(BookingCleanupService.class);

    @Autowired
    private AvailabilityCalendarRepository availabilityCalendarRepository;

    /**
     * Scheduled job to cleanup expired holds every 5 minutes
     * This prevents accumulation of stale hold records
     */
    @Scheduled(fixedRate = 300000) // 5 minutes = 300,000 milliseconds
    @Transactional
    public void cleanupExpiredHolds() {
        logger.info("=== STARTING EXPIRED HOLDS CLEANUP ===");

        LocalDateTime now = LocalDateTime.now();
        int cleanedCount = 0;

        try {
            // Find all HOLD records that have expired
            List<AvailabilityCalendar> expiredHolds = availabilityCalendarRepository
                .findByStatusAndHoldExpireAtBefore("HOLD", now);

            logger.info("Found {} expired hold records to clean up", expiredHolds.size());

            for (AvailabilityCalendar cal : expiredHolds) {
                logger.debug("Cleaning up expired hold: listing={}, day={}, token={}, expired={}",
                    cal.getId().getListingId(), cal.getId().getDay(),
                    cal.getHoldToken(), cal.getHoldExpireAt());

                // Reset to FREE status and clear hold data
                cal.setStatus("FREE");
                cal.setHoldToken(null);
                cal.setHoldExpireAt(null);
                availabilityCalendarRepository.save(cal);
                cleanedCount++;
            }

            if (cleanedCount > 0) {
                logger.info("Successfully cleaned up {} expired hold records", cleanedCount);
            } else {
                logger.debug("No expired holds found to clean up");
            }

        } catch (Exception e) {
            logger.error("Error during expired holds cleanup", e);
        }

        logger.info("=== EXPIRED HOLDS CLEANUP COMPLETED ===");
    }

    /**
     * Manual cleanup method for testing/admin purposes
     */
    @Transactional
    public int cleanupExpiredHoldsManual() {
        logger.info("Manual expired holds cleanup initiated");

        LocalDateTime now = LocalDateTime.now();
        List<AvailabilityCalendar> expiredHolds = availabilityCalendarRepository
            .findByStatusAndHoldExpireAtBefore("HOLD", now);

        int cleanedCount = 0;
        for (AvailabilityCalendar cal : expiredHolds) {
            cal.setStatus("FREE");
            cal.setHoldToken(null);
            cal.setHoldExpireAt(null);
            availabilityCalendarRepository.save(cal);
            cleanedCount++;
        }

        logger.info("Manual cleanup completed: {} records cleaned", cleanedCount);
        return cleanedCount;
    }
}
