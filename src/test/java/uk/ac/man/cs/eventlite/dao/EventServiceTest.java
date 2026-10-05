package uk.ac.man.cs.eventlite.dao;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit4.AbstractTransactionalJUnit4SpringContextTests;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import uk.ac.man.cs.eventlite.entities.Venue;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import uk.ac.man.cs.eventlite.entities.Event;



import uk.ac.man.cs.eventlite.EventLite;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = EventLite.class)
@DirtiesContext
@ActiveProfiles("test")
//@Disabled
public class EventServiceTest extends AbstractTransactionalJUnit4SpringContextTests {

	@Autowired
	private EventService eventService;
    @Autowired
    private VenueService venueService;
    private Venue testVenue;

    @BeforeEach
    public void prepareIndependentEvents() {
        testVenue = venueService.findAll().iterator().next();
        eventService.findAll().forEach(event -> eventService.deleteById(event.getId()));
    }

    private Event saveWithVenue(Event event) {
        event.setVenue(testVenue);
        return eventService.save(event);
    }

	// This class is here as a starter for testing any custom methods within the
	// EventService. Note: It is currently @Disabled!
    @Test
    @Transactional
    public void testCount() {
        long initialCount = eventService.count();

        Event event = new Event();
        event.setName("Integration Test Event");
        event.setDate(LocalDate.now());
        event.setTime(LocalTime.now());
        saveWithVenue(event);

        assertEquals(initialCount + 1, eventService.count(),
            "Event count should increase by one after saving a new event.");
    }


    @Test
    @Transactional
    public void testFindAllAndFindById() {
        Event event = new Event();
        event.setName("Test Event");
        event.setDate(LocalDate.now());
        event.setTime(LocalTime.now());

        Event savedEvent = saveWithVenue(event);
        Optional<Event> retrievedEvent = eventService.findById(savedEvent.getId());

        assertTrue(retrievedEvent.isPresent(), "Saved event should be retrievable by id.");
        assertEquals("Test Event", retrievedEvent.get().getName(), "Event name should match.");

        Iterable<Event> events = eventService.findAll();
        assertTrue(events.iterator().hasNext(), "findAll() should return at least one event.");
    }

    @Test
    @Transactional
    public void testFindByNameContainingIgnoreCase() {
        String uniqueName = "UniqueTestEventName";
        Event event = new Event();
        event.setName(uniqueName);
        event.setDate(LocalDate.now());
        event.setTime(LocalTime.now());

        saveWithVenue(event);

        List<Event> events = eventService.findByNameContainingIgnoreCase("uniquetest");
        assertFalse(events.isEmpty(), "Search should return at least one event.");
        assertEquals(uniqueName, events.get(0).getName(), "The returned event should match the unique name.");
    }

    @Test
    @Transactional
    public void testFindTop3EventsWhenMoreThanThreeUpcoming() {
        Event pastEvent = new Event();
        pastEvent.setName("Past Event");
        pastEvent.setDate(LocalDate.now().minusDays(1));
        pastEvent.setTime(LocalTime.now());
        saveWithVenue(pastEvent);

        Event future1 = new Event();
        future1.setName("Future Event A");
        future1.setDate(LocalDate.now().plusDays(1));
        future1.setTime(LocalTime.now());
        saveWithVenue(future1);

        Event future2 = new Event();
        future2.setName("Future Event B");
        future2.setDate(LocalDate.now().plusDays(2));
        future2.setTime(LocalTime.now());
        saveWithVenue(future2);

        Event future3 = new Event();
        future3.setName("Future Event C");
        future3.setDate(LocalDate.now().plusDays(3));
        future3.setTime(LocalTime.now());
        saveWithVenue(future3);

        Event future4 = new Event();
        future4.setName("Future Event D");
        future4.setDate(LocalDate.now().plusDays(4));
        future4.setTime(LocalTime.now());
        saveWithVenue(future4);

        List<Event> top3 = eventService.findTop3Events();
        assertEquals(3, top3.size(), "Should return 3 future events");
        assertEquals("Future Event A", top3.get(0).getName());
        assertEquals("Future Event B", top3.get(1).getName());
        assertEquals("Future Event C", top3.get(2).getName());
    }


    @Test
    @Transactional
    public void testFindTop3EventsWhenFewerThanThreeUpcoming() {
        Event pastEvent = new Event();
        pastEvent.setName("Past Event");
        pastEvent.setDate(LocalDate.now().minusDays(2));
        pastEvent.setTime(LocalTime.now());
        saveWithVenue(pastEvent);

        Event future1 = new Event();
        future1.setName("Future Event A");
        future1.setDate(LocalDate.now().plusDays(1));
        future1.setTime(LocalTime.now());
        saveWithVenue(future1);

        List<Event> topEvents = eventService.findTop3Events();
        assertEquals(1, topEvents.size(), "When there is only one future event, that event should be returned");
        assertEquals("Future Event A", topEvents.get(0).getName());
    }

}
