package com.harshith.event_booking.controller;

import com.harshith.event_booking.dto.EventResponse;
import com.harshith.event_booking.dto.PageResponse;
import com.harshith.event_booking.dto.SeatResponse;
import com.harshith.event_booking.service.EventService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public PageResponse<EventResponse> listEvents(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return eventService.listEvents(page, size);
    }

    @GetMapping("/{id}")
    public EventResponse getEvent(@PathVariable Long id) {
        return eventService.getEvent(id);
    }

    @GetMapping("/{id}/seats")
    public List<SeatResponse> getSeats(@PathVariable Long id) {
        return eventService.getSeats(id);
    }
}
