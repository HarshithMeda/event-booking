package com.harshith.event_booking.service;

import com.harshith.event_booking.dto.EventResponse;
import com.harshith.event_booking.dto.PageResponse;
import com.harshith.event_booking.dto.SeatResponse;
import com.harshith.event_booking.exception.EventNotFoundException;
import com.harshith.event_booking.model.Event;
import com.harshith.event_booking.model.Seat;
import com.harshith.event_booking.model.SeatStatus;
import com.harshith.event_booking.repository.EventRepository;
import com.harshith.event_booking.repository.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock EventRepository eventRepository;
    @Mock SeatRepository seatRepository;

    private EventService service;

    @BeforeEach
    void setUp() {
        service = new EventService(eventRepository, seatRepository);
    }

    @Test
    void listEvents_alwaysSortsByStartTimeThenId_andReportsPageInfo() {
        Event jazz = event(2L, "Jazz in the Park", "2026-11-20T18:00:00Z");
        Event rock = event(1L, "Rock Night Live", "2026-12-05T19:30:00Z");
        when(eventRepository.findAll(any(Pageable.class)))
                .thenAnswer(call -> new PageImpl<>(List.of(jazz, rock), call.getArgument(0), 3));

        PageResponse<EventResponse> page = service.listEvents(0, 2);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(eventRepository).findAll(pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(2);
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by("startsAt").ascending().and(Sort.by("id").ascending()));

        assertThat(page.content()).extracting(EventResponse::name).containsExactly("Jazz in the Park", "Rock Night Live");
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(2);
    }

    @Test
    void getEvent_returnsIt_orIs404() {
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event(1L, "Rock Night Live", "2026-12-05T19:30:00Z")));
        when(eventRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(service.getEvent(1L).name()).isEqualTo("Rock Night Live");
        assertThatThrownBy(() -> service.getEvent(99L))
                .isInstanceOf(EventNotFoundException.class)
                .hasMessage("Event not found: 99");
    }

    @Test
    void getSeats_forAMissingEvent_is404_insteadOfAnEmptyList() {
        when(eventRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.getSeats(99L)).isInstanceOf(EventNotFoundException.class);
        verifyNoInteractions(seatRepository);
    }

    @Test
    void getSeats_returnsSeatsInRowAndNumberOrder() {
        Event rock = event(1L, "Rock Night Live", "2026-12-05T19:30:00Z");
        when(eventRepository.existsById(1L)).thenReturn(true);
        when(seatRepository.findByEventIdOrderBySeatRowAscSeatNumberAsc(1L))
                .thenReturn(List.of(seat(11L, rock, "A", 1, SeatStatus.AVAILABLE), seat(12L, rock, "A", 2, SeatStatus.HELD)));

        assertThat(service.getSeats(1L)).containsExactly(
                new SeatResponse(11L, "A", 1, 7500, SeatStatus.AVAILABLE),
                new SeatResponse(12L, "A", 2, 7500, SeatStatus.HELD));
    }

    private static Event event(long id, String name, String startsAt) {
        Event event = new Event();
        ReflectionTestUtils.setField(event, "id", id);
        event.setName(name);
        event.setVenue("Venue");
        event.setStartsAt(Instant.parse(startsAt));
        return event;
    }

    private static Seat seat(long id, Event event, String row, int number, SeatStatus status) {
        Seat seat = new Seat();
        ReflectionTestUtils.setField(seat, "id", id);
        seat.setEvent(event);
        seat.setSeatRow(row);
        seat.setSeatNumber(number);
        seat.setPriceCents(7500);
        seat.setStatus(status);
        return seat;
    }
}
