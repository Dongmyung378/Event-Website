package uk.ac.man.cs.eventlite.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;

import uk.ac.man.cs.eventlite.config.TimeConfiguration;
import uk.ac.man.cs.eventlite.entities.Event;
import uk.ac.man.cs.eventlite.entities.Venue;
import uk.ac.man.cs.eventlite.services.GeocodingService;

@DataJpaTest(properties = {
    "spring.jpa.properties.hibernate.generate_statistics=true",
    "spring.jpa.properties.jakarta.persistence.validation.mode=none",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({EventServiceImpl.class, VenueServiceImpl.class, TimeConfiguration.class,
    ScheduleQueryIntegrationTest.FixedTime.class})
class ScheduleQueryIntegrationTest {
    private static final LocalDate TODAY = LocalDate.of(2031, 1, 1);

    @Autowired private EntityManager entityManager;
    @Autowired private EventService eventService;
    @Autowired private VenueService venueService;
    @MockitoBean private GeocodingService geocodingService;

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedTime {
        @Bean @Primary
        Clock testScheduleClock() {
            return Clock.fixed(Instant.parse("2031-01-01T00:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Test
    void upcomingUsesClockIncludesTodayAndOrdersTimeCaseAndIdInOneQuery() {
        Venue venue = venue("Venue");
        event(venue, "past", TODAY.minusDays(1), LocalTime.NOON);
        event(venue, "unknown date", null, LocalTime.NOON);
        Event alphaFirst = event(venue, "alpha", TODAY, LocalTime.NOON);
        Event alphaSecond = event(venue, "ALPHA", TODAY, LocalTime.NOON);
        Event beta = event(venue, "Beta", TODAY, LocalTime.NOON);
        event(venue, "A later time", TODAY, LocalTime.of(18, 0));
        event(venue, "A no time", TODAY, null);
        event(venue, "tomorrow", TODAY.plusDays(1), LocalTime.of(9, 0));
        Statistics statistics = freshStatistics();

        assertEquals(List.of(alphaFirst.getId(), alphaSecond.getId(), beta.getId()),
            ids(eventService.findTop3Events()));
        assertEquals(1, statistics.getPrepareStatementCount());
        assertTrue(statistics.getEntityLoadCount() <= 6, "Only three events and their venues should be loaded");
        assertEquals(0, statistics.getCollectionLoadCount());
    }

    @Test
    void venueUpcomingIsBoundedAndMatchesGlobalOrdering() {
        Venue venue = venue("Venue");
        Event today = event(venue, "Today", TODAY, null);
        Event alpha = event(venue, "alpha", TODAY.plusDays(1), LocalTime.NOON);
        Event beta = event(venue, "Beta", TODAY.plusDays(1), LocalTime.NOON);
        event(venue, "later", TODAY.plusDays(2), null);
        event(venue, "past", TODAY.minusDays(1), null);
        Venue other = venue("Other venue");
        event(other, "Other", TODAY.plusDays(3), null);
        Statistics statistics = freshStatistics();

        assertEquals(List.of(today.getId(), alpha.getId(), beta.getId()),
            ids(venueService.findNext3Events(venue.getId())));
        assertEquals(1, statistics.getPrepareStatementCount());
        assertEquals(List.of(), venueService.findNext3Events(Long.MAX_VALUE));
    }

    @Test
    void completeVenueScheduleKeepsPastEventsAndUsesSameStableOrdering() {
        Venue venue = venue("Venue");
        Event noTime = event(venue, "alpha", TODAY, null);
        Event beta = event(venue, "Beta", TODAY, LocalTime.NOON);
        Event alpha = event(venue, "alpha", TODAY, LocalTime.NOON);
        Event past = event(venue, "Past", TODAY.minusDays(1), null);
        Event noDate = event(venue, "Unknown date", null, null);
        freshStatistics();
        assertEquals(List.of(past.getId(), alpha.getId(), beta.getId(), noTime.getId(), noDate.getId()),
            ids(eventService.getEventsByVenueId(venue.getId())));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    void popularityHandlesZeroThroughFourVenuesWithoutLoadingTheirEvents(int count) {
        List<Venue> venues = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Venue venue = venue("Venue " + i);
            venues.add(venue);
            for (int j = 0; j < i; j++) { event(venue, "Event " + j, TODAY, null); }
        }
        Statistics statistics = freshStatistics();
        List<Venue> popular = venueService.findTop3Venues();
        assertEquals(Math.min(3, count), popular.size());
        if (count > 0) { assertEquals(venues.get(count - 1).getId(), popular.get(0).getId()); }
        assertEquals(1, statistics.getPrepareStatementCount());
        assertEquals(0, statistics.getCollectionLoadCount());
        assertEquals(popular.size(), statistics.getEntityLoadCount());
    }

    @Test
    void popularityIncludesEmptyVenuesAndBreaksTiesByCaseInsensitiveNameThenId() {
        Venue beta = venue("Beta");
        Venue alpha = venue("alpha");
        Venue alphaSecond = venue("ALPHA");
        freshStatistics();
        assertEquals(List.of(alpha.getId(), alphaSecond.getId(), beta.getId()),
            venueService.findTop3Venues().stream().map(Venue::getId).toList());
    }

    @Test
    void pagesAreBoundedAndStableAcrossIdenticalNamesAndSearchKeepsLiteralCharacters() {
        Venue venue = venue("Venue");
        List<Long> equalIds = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            equalIds.add(event(venue, i % 2 == 0 ? "alpha" : "ALPHA", TODAY, LocalTime.NOON).getId());
        }
        Event percent = event(venue, "100% Review", TODAY, null);
        Event underscore = event(venue, "Under_score", TODAY, null);
        Event bang = event(venue, "Bang!", TODAY, null);
        Event slash = event(venue, "Back\\Slash", TODAY, null);
        freshStatistics();

        Page<Event> first = eventService.findPage(" alpha ", 0, 3);
        Page<Event> second = eventService.findPage("ALPHA", 1, 3);
        assertEquals(8, first.getTotalElements());
        assertEquals(equalIds.subList(0, 3), ids(first.getContent()));
        assertEquals(equalIds.subList(3, 6), ids(second.getContent()));
        HashSet<Long> seen = new HashSet<>(ids(first.getContent()));
        assertTrue(seen.addAll(ids(second.getContent())));
        assertEquals(6, seen.size());
        assertEquals(List.of(percent.getId()), ids(eventService.findPage("%", 0, 100).getContent()));
        assertEquals(List.of(underscore.getId()), ids(eventService.findPage("_", 0, 100).getContent()));
        assertEquals(List.of(bang.getId()), ids(eventService.findPage("!", 0, 100).getContent()));
        assertEquals(List.of(slash.getId()), ids(eventService.findPage("\\", 0, 100).getContent()));
        assertEquals(12, eventService.findPage(null, 0, 100).getTotalElements());
        assertEquals(12, eventService.findPage("   ", 0, 100).getTotalElements());
    }

    @Test
    void venuePagesKeepLiteralSearchAndNeverLoadEventCollections() {
        venue("100% Hall");
        Venue underscore = venue("Under_score Hall");
        venue("Beta Hall");
        venue("alpha Hall");
        Statistics statistics = freshStatistics();
        Page<Venue> all = venueService.findPage(null, 0, 2);
        assertEquals(2, all.getNumberOfElements());
        assertEquals(4, all.getTotalElements());
        assertEquals(2, statistics.getPrepareStatementCount());
        assertEquals(0, statistics.getCollectionLoadCount());
        assertEquals(List.of(underscore.getId()),
            venueService.findPage("_", 0, 100).stream().map(Venue::getId).toList());
    }

    @Test
    void pageQueryLoadsOneBoundedResultWithVenueAndOneCount() {
        Venue venue = venue("Venue");
        for (int i = 0; i < 100; i++) { event(venue, String.format("Event %03d", i), TODAY, null); }
        Statistics statistics = freshStatistics();
        Page<Event> page = eventService.findPage(null, 3, 5);
        assertEquals(100, page.getTotalElements());
        assertEquals(5, page.getNumberOfElements());
        assertEquals(2, statistics.getPrepareStatementCount());
        assertEquals(6, statistics.getEntityLoadCount());
        assertEquals(0, statistics.getCollectionLoadCount());
        assertEquals("Event 015", page.getContent().get(0).getName());
    }

    @Test
    void invalidPageParametersFailBeforeIssuingSql() {
        Statistics statistics = freshStatistics();
        assertThrows(IllegalArgumentException.class, () -> eventService.findPage(null, -1, 10));
        assertThrows(IllegalArgumentException.class, () -> eventService.findPage(null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> venueService.findPage(null, 0, 101));
        assertThrows(IllegalArgumentException.class, () -> venueService.findPage(null, -1, 10));
        assertEquals(0, statistics.getPrepareStatementCount());
    }

    private Venue venue(String name) {
        Venue venue = new Venue();
        venue.setName(name);
        venue.setCapacity(100);
        venue.setAddress("Road", "M1");
        entityManager.persist(venue);
        return venue;
    }

    private Event event(Venue venue, String name, LocalDate date, LocalTime time) {
        Event event = new Event();
        event.setVenue(venue);
        event.setName(name);
        event.setDate(date);
        event.setTime(time);
        entityManager.persist(event);
        return event;
    }

    private Statistics freshStatistics() {
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        return statistics;
    }

    private List<Long> ids(List<Event> events) { return events.stream().map(Event::getId).toList(); }
}
