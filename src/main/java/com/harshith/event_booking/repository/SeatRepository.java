package com.harshith.event_booking.repository;

import com.harshith.event_booking.model.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByEventIdOrderBySeatRowAscSeatNumberAsc(Long eventId);
}
