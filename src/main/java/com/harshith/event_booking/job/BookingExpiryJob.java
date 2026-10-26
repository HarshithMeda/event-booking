package com.harshith.event_booking.job;

import com.harshith.event_booking.service.BookingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Releases seats from holds whose 10 minutes have passed. */
@Component
public class BookingExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(BookingExpiryJob.class);

    private final BookingService bookingService;

    public BookingExpiryJob(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Scheduled(fixedDelayString = "${app.booking.expiry-check-interval:PT30S}")
    public void releaseExpiredHolds() {
        int expired = 0;
        for (Long bookingId : bookingService.findExpiredHoldIds()) {
            try {
                // One transaction per booking, so one conflict doesn't undo the others.
                if (bookingService.expireHold(bookingId)) {
                    expired++;
                }
            } catch (ConcurrencyFailureException e) {
                // The user confirmed or cancelled at the same moment; their change won.
                log.info("Skipped expiring booking {}: it was changed at the same time", bookingId);
            }
        }
        if (expired > 0) {
            log.info("Expired {} hold(s) and released their seats", expired);
        }
    }
}
