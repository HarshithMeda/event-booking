package com.harshith.event_booking.repository;

import com.harshith.event_booking.model.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByEventIdOrderBySeatRowAscSeatNumberAsc(Long eventId);

    /** Sorted by id so every request updates seats in the same order (prevents deadlocks). */
    List<Seat> findByIdInOrderByIdAsc(Collection<Long> ids);
}
