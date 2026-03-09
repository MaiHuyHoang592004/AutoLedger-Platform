package com.group1.car_rental.repository;

import com.group1.car_rental.entity.OutboxEvents;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface OutboxEventsRepository extends JpaRepository<OutboxEvents, Long> {

    @Query("SELECT o FROM OutboxEvents o WHERE o.processedAt IS NULL ORDER BY o.createdAt ASC")
    List<OutboxEvents> findUnprocessedEvents();

    List<OutboxEvents> findByAggregateTypeAndAggregateIdAndEventType(String aggregateType, Long aggregateId, String eventType);
}
