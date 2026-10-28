package com.harshith.event_booking.service;

import com.harshith.event_booking.cache.CacheNames;
import com.harshith.event_booking.dto.EventResponse;
import com.harshith.event_booking.dto.PageResponse;
import com.harshith.event_booking.dto.SeatResponse;
import com.harshith.event_booking.exception.EventNotFoundException;
import com.harshith.event_booking.repository.EventRepository;
import com.harshith.event_booking.repository.SeatRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class EventService {

    // Always sort by start time; id breaks ties so paging is stable.
    private static final Sort BY_START_TIME = Sort.by("startsAt").ascending().and(Sort.by("id").ascending());

    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;

    public EventService(EventRepository eventRepository, SeatRepository seatRepository) {
        this.eventRepository = eventRepository;
        this.seatRepository = seatRepository;
    }

    @Cacheable(cacheNames = CacheNames.EVENT_PAGES, key = "#page + ':' + #size")
    public PageResponse<EventResponse> listEvents(int page, int size) {
        var events = eventRepository.findAll(PageRequest.of(page, size, BY_START_TIME))
                .map(EventResponse::from);
        return PageResponse.from(events);
    }

    @Cacheable(cacheNames = CacheNames.EVENTS, key = "#id")
    public EventResponse getEvent(Long id) {
        return eventRepository.findById(id)
                .map(EventResponse::from)
                .orElseThrow(() -> new EventNotFoundException(id));
    }

    /** Display only. Cleared after every booking change (see SeatMapCacheEvictor); never used by booking logic. */
    @Cacheable(cacheNames = CacheNames.SEAT_MAPS, key = "#eventId")
    public List<SeatResponse> getSeats(Long eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new EventNotFoundException(eventId);
        }
        return seatRepository.findByEventIdOrderBySeatRowAscSeatNumberAsc(eventId).stream()
                .map(SeatResponse::from)
                .toList();
    }
}
