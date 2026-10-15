package uk.ac.man.cs.eventlite.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.test.util.ReflectionTestUtils;

import uk.ac.man.cs.eventlite.config.IntegrationViewConfig;
import uk.ac.man.cs.eventlite.dao.EventRepository;
import uk.ac.man.cs.eventlite.dao.EventServiceImpl;
import uk.ac.man.cs.eventlite.dao.MastodonService;
import uk.ac.man.cs.eventlite.dao.VenueServiceImpl;
import uk.ac.man.cs.eventlite.entities.Event;

class ScheduleClockTest {
    @ParameterizedTest
    @CsvSource({"2030-12-31T23:59:59Z,2030-12-31", "2031-01-01T00:00:00Z,2031-01-01"})
    void scheduleQueriesAndViewModelUseTheSameInjectedMidnightBoundary(String instant, LocalDate expectedDate) {
        Clock clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
        EventRepository repository = mock(EventRepository.class);
        EventServiceImpl events = new EventServiceImpl();
        ReflectionTestUtils.setField(events, "eventRepository", repository);
        ReflectionTestUtils.setField(events, "clock", clock);
        VenueServiceImpl venues = new VenueServiceImpl();
        ReflectionTestUtils.setField(venues, "eventRepository", repository);
        ReflectionTestUtils.setField(venues, "clock", clock);
        events.findTop3Events();
        venues.findNext3Events(12L);
        verify(repository).findUpcoming(expectedDate, PageRequest.of(0, 3));
        verify(repository).findUpcomingAtVenue(12L, expectedDate, PageRequest.of(0, 3));

        @SuppressWarnings("unchecked")
        ObjectProvider<MastodonService> provider = mock(ObjectProvider.class);
        IntegrationViewConfig views = new IntegrationViewConfig("", provider, clock);
        ExtendedModelMap model = new ExtendedModelMap();
        views.eventDates(model);
        assertEquals(expectedDate, model.get("currentDate"));
        assertEquals(expectedDate.plusDays(1), model.get("earliestEventDate"));
    }

    @Test
    void injectedZoneDeterminesTodayWithoutDependingOnTheTestMachine() {
        Clock clock = Clock.fixed(Instant.parse("2030-12-31T23:59:59Z"), ZoneId.of("Asia/Seoul"));
        EventRepository repository = mock(EventRepository.class);
        EventServiceImpl events = new EventServiceImpl();
        ReflectionTestUtils.setField(events, "eventRepository", repository);
        ReflectionTestUtils.setField(events, "clock", clock);
        events.findTop3Events();
        verify(repository).findUpcoming(LocalDate.of(2031, 1, 1), PageRequest.of(0, 3));
    }

    @Test
    void inMemoryCompatibilityOrderingUsesIdForCaseInsensitiveNameTies() {
        LocalDate today = LocalDate.of(2031, 1, 1);
        Event second = new Event();
        second.setId(2L); second.setName("ALPHA"); second.setDate(today);
        Event first = new Event();
        first.setId(1L); first.setName("alpha"); first.setDate(today);
        assertEquals(List.of(first, second), EventSchedule.upcomingEvents(List.of(second, first), today));
    }
}
