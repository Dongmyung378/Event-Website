package uk.ac.man.cs.eventlite.config.data;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import uk.ac.man.cs.eventlite.dao.EventService;
import uk.ac.man.cs.eventlite.dao.VenueService;
import uk.ac.man.cs.eventlite.entities.Event;
import uk.ac.man.cs.eventlite.entities.Venue;

class InitialDataLoaderTest {
    @Test
    void emptyDatabaseUsesSavedVenueInstances() throws Exception {
        EventService eventService = mock(EventService.class);
        VenueService venueService = mock(VenueService.class);
        when(venueService.findAll()).thenReturn(Collections.emptyList());
        List<Venue> savedVenues = new ArrayList<>();
        when(venueService.save(any())).thenAnswer(call -> {
            Venue input = call.getArgument(0);
            Venue saved = new Venue();
            saved.setId(100L + savedVenues.size());
            saved.setName(input.getName());
            savedVenues.add(saved);
            return saved;
        });
        List<Event> savedEvents = new ArrayList<>();
        when(eventService.save(any())).thenAnswer(call -> {
            Event event = call.getArgument(0);
            savedEvents.add(event);
            return event;
        });
        InitialDataLoader loader = new InitialDataLoader();
        ReflectionTestUtils.setField(loader, "eventService", eventService);
        ReflectionTestUtils.setField(loader, "venueService", venueService);
        loader.initDatabase().run();
        assertEquals(4, savedVenues.size());
        assertEquals(7, savedEvents.size());
        assertTrue(savedEvents.stream().allMatch(event -> savedVenues.contains(event.getVenue())));
    }

    @Test
    void partialDatabaseGetsNonNullVenueReferencesAndSeedingIsIdempotent() throws Exception {
        EventService eventService = mock(EventService.class);
        VenueService venueService = mock(VenueService.class);
        Venue existing = new Venue();
        existing.setId(1L);
        when(venueService.findAll()).thenReturn(Collections.singletonList(existing));
        List<Event> saved = new ArrayList<>();
        when(eventService.count()).thenAnswer(call -> (long) saved.size());
        when(eventService.save(any())).thenAnswer(call -> {
            Event event = call.getArgument(0);
            saved.add(event);
            return event;
        });
        InitialDataLoader loader = new InitialDataLoader();
        ReflectionTestUtils.setField(loader, "eventService", eventService);
        ReflectionTestUtils.setField(loader, "venueService", venueService);
        loader.initDatabase().run();
        loader.initDatabase().run();
        assertEquals(7, saved.size());
        assertTrue(saved.stream().allMatch(event -> event.getVenue() == existing));
        assertEquals(4, saved.stream().filter(event -> event.getDate().isAfter(LocalDate.now())).count());
        assertEquals(3, saved.stream().filter(event -> event.getDate().isBefore(LocalDate.now())).count());
        verify(venueService, never()).save(any());
    }
}
