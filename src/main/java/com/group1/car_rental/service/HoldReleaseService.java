package com.group1.car_rental.service;

import com.group1.car_rental.entity.AvailabilityCalendar;
import com.group1.car_rental.entity.OutboxEvents;
import com.group1.car_rental.repository.AvailabilityCalendarRepository;
import com.group1.car_rental.repository.OutboxEventsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class HoldReleaseService {

    private static final Logger logger = LoggerFactory.getLogger(HoldReleaseService.class);

    @Autowired
    private AvailabilityCalendarRepository availabilityCalendarRepository;

    @Autowired
    private OutboxEventsRepository outboxEventsRepository;

    // Run every minute
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void releaseExpiredHolds() {
        LocalDateTime now = LocalDateTime.now();
        List<AvailabilityCalendar> expiredHolds = availabilityCalendarRepository
            .findByStatusAndHoldExpireAtBefore("HOLD", now);

        if (!expiredHolds.isEmpty()) {
            logger.warn("⏰ Found {} expired holds to release", expiredHolds.size());

            // Group by listing for better logging
            java.util.Map<Long, Long> expiredByListing = expiredHolds.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    cal -> cal.getId().getListingId(),
                    java.util.stream.Collectors.counting()
                ));

            for (AvailabilityCalendar cal : expiredHolds) {
                logger.info("Releasing expired hold: token={} listing={} day={} expired={}",
                    cal.getHoldToken(), cal.getId().getListingId(), cal.getId().getDay(), cal.getHoldExpireAt());

                cal.setStatus("FREE");
                cal.setHoldToken(null);
                cal.setHoldExpireAt(null);
                availabilityCalendarRepository.save(cal);
            }

            logger.info("Successfully released {} expired holds across {} listings",
                expiredHolds.size(), expiredByListing.size());

            // Publish outbox event for monitoring
            outboxEventsRepository.save(new OutboxEvents("System", 0L,
                "HOLDS_EXPIRED", "{\"expiredCount\": " + expiredHolds.size() +
                ", \"listingsAffected\": " + expiredByListing.size() + "}"));

            // Alert if too many holds are expiring (potential UX issue)
            if (expiredHolds.size() > 10) {
                logger.warn("🚨 HIGH HOLD EXPIRATION RATE: {} holds expired in this cycle. " +
                    "This may indicate users are abandoning bookings during the hold period.",
                    expiredHolds.size());
            }
        }
    }
}
