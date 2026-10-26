package com.harshith.event_booking.repository;

import com.harshith.event_booking.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {
}
