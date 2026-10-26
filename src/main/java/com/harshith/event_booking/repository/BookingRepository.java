package com.harshith.event_booking.repository;

import com.harshith.event_booking.model.Booking;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    /** Always look bookings up by id AND owner, so other users' bookings are invisible (404). */
    @EntityGraph(attributePaths = {"event", "seats"})
    Optional<Booking> findByIdAndUserId(Long id, Long userId);

    /** Loads event and seats in the same query, so listing doesn't run one query per booking. */
    @EntityGraph(attributePaths = {"event", "seats"})
    List<Booking> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    @Query("select b.id from Booking b where b.status = com.harshith.event_booking.model.BookingStatus.HELD "
            + "and b.heldUntil < :now order by b.id")
    List<Long> findExpiredHoldIds(@Param("now") Instant now);
}
