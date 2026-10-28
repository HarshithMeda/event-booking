package com.harshith.event_booking.cache;

public final class CacheNames {

    /** One page of GET /api/events. Key: "page:size". */
    public static final String EVENT_PAGES = "eventPages";
    /** GET /api/events/{id}. Key: event id. */
    public static final String EVENTS = "events";
    /** GET /api/events/{id}/seats. Key: event id. Cleared after every seat change. */
    public static final String SEAT_MAPS = "seatMaps";

    private CacheNames() {
    }
}
