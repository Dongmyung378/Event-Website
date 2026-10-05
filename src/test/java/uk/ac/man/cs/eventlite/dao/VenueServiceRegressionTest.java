package uk.ac.man.cs.eventlite.dao;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import uk.ac.man.cs.eventlite.entities.Event;
import uk.ac.man.cs.eventlite.entities.Venue;
import uk.ac.man.cs.eventlite.services.Coordinates;
import uk.ac.man.cs.eventlite.services.GeocodingService;

class VenueServiceRegressionTest {
    private VenueRepository repository;
    private GeocodingService geocoder;
    private VenueServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(VenueRepository.class);
        geocoder = mock(GeocodingService.class);
        service = new VenueServiceImpl();
        ReflectionTestUtils.setField(service, "venueRepository", repository);
        ReflectionTestUtils.setField(service, "geocodingService", geocoder);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    void popularVenuesHandlesSmallDatabases(int count) {
        List<Venue> venues = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Venue venue = venue("Venue " + i, "Road", "M1");
            for (int j = 0; j < i; j++) venue.getEvents().add(new Event());
            venues.add(venue);
        }
        when(repository.findAllByOrderByNameAsc()).thenReturn(venues);
        List<Venue> result = service.findTop3Venues();
        assertEquals(Math.min(3, count), result.size());
        if (count > 0) assertSame(venues.get(count - 1), result.get(0));
    }

    @Test
    void changedAddressWithFailedLookupClearsPreviousCoordinates() throws Exception {
        Venue original = venue("Venue", "Old Road", "OLD");
        original.setId(1L);
        Venue changed = venue("Venue", "New Road", "NEW");
        changed.setId(1L);
        changed.setLatitude(53.48);
        changed.setLongitude(-2.24);
        when(repository.findById(1L)).thenReturn(Optional.of(original));
        when(geocoder.getCoordinates("New Road", "NEW")).thenReturn(new Coordinates());
        service.save(changed);
        assertNull(changed.getLatitude());
        assertNull(changed.getLongitude());
        verify(repository).save(changed);
    }

    @Test
    void unchangedAddressKeepsCoordinatesWithoutExternalLookup() throws Exception {
        Venue existing = venue("Renamed Venue", "Road", "M1");
        existing.setId(1L);
        existing.setLatitude(53.48);
        existing.setLongitude(-2.24);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        service.save(existing);
        assertEquals(53.48, existing.getLatitude());
        assertEquals(-2.24, existing.getLongitude());
        verifyNoInteractions(geocoder);
    }

    @Test
    void nextThreeIncludesTodayAndSortsSameDayByTime() {
        LocalDate today = LocalDate.now();
        Venue venue = venue("Venue", "Road", "M1");
        Event todayEvent = event(today, LocalTime.NOON);
        Event early = event(today.plusDays(1), LocalTime.of(9, 0));
        Event late = event(today.plusDays(1), LocalTime.of(18, 0));
        venue.getEvents().addAll(Arrays.asList(event(today.minusDays(1), LocalTime.NOON),
            event(today.plusDays(2), null), late, early, todayEvent));
        when(repository.findById(1L)).thenReturn(Optional.of(venue));
        assertEquals(Arrays.asList(todayEvent, early, late), service.findNext3Events(1L));
    }

    private Venue venue(String name, String road, String postcode) {
        Venue venue = new Venue();
        venue.setName(name);
        venue.setAddress(road, postcode);
        venue.setCapacity(100);
        return venue;
    }

    private Event event(LocalDate date, LocalTime time) {
        Event event = new Event();
        event.setDate(date);
        event.setTime(time);
        event.setName("Event");
        return event;
    }
}
