package com.harshith.event_booking.cache;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Clears an event's cached seat map AFTER the booking transaction commits.
 * Evicting before the commit would let another request re-cache the old seats in between.
 * If the transaction rolls back (for example a 409), nothing changed, so nothing is evicted.
 */
@Component
public class SeatMapCacheEvictor {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @CacheEvict(cacheNames = CacheNames.SEAT_MAPS, key = "#event.eventId()")
    public void onSeatsChanged(SeatsChangedEvent event) {
        // The @CacheEvict annotation does the work.
    }
}
