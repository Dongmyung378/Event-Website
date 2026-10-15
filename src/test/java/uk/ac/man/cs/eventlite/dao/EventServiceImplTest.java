package uk.ac.man.cs.eventlite.dao;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.data.domain.Pageable;

import uk.ac.man.cs.eventlite.entities.Event;
import uk.ac.man.cs.eventlite.entities.Venue;

public class EventServiceImplTest {

    private EventRepository eventRepository;
    private EventServiceImpl eventService;

    @BeforeEach
    public void setup() throws Exception {
        eventRepository = mock(EventRepository.class);
        eventService = new EventServiceImpl();
        Field field = EventServiceImpl.class.getDeclaredField("eventRepository");
        field.setAccessible(true);
        field.set(eventService, eventRepository);
    }

    @Test
    public void testCount() {
        when(eventRepository.count()).thenReturn(2L);
        assertEquals(2L, eventService.count());
    }

    @Test
    public void testFindAll() {
        Event event1 = new Event();
        event1.setId(1L);
        event1.setName("Event 1");
        Event event2 = new Event();
        event2.setId(2L);
        event2.setName("Event 2");
        when(eventRepository.findAllByOrderByDateAscNameAsc()).thenReturn(Arrays.asList(event1, event2));

        Iterable<Event> events = eventService.findAll();
        assertNotNull(events);
        assertEquals(2, ((List<Event>) events).size());
    }

    @Test
    public void testFindById() {
        Event event = new Event();
        event.setId(1L);
        event.setName("Event 1");
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

        Optional<Event> result = eventService.findById(1L);
        assertTrue(result.isPresent());
        assertEquals("Event 1", result.get().getName());
    }

    @Test
    public void testSave() {
        Event event = new Event();
        event.setId(1L);
        event.setName("Event 1");
        when(eventRepository.save(event)).thenReturn(event);

        Event saved = eventService.save(event);
        assertEquals(event, saved);
    }

    @Test
    public void testFindByNameContainingIgnoreCase() {
        Event event = new Event();
        event.setId(1L);
        event.setName("Test Event");
        when(eventRepository.findByNameContainingIgnoreCase("test")).thenReturn(Arrays.asList(event));

        List<Event> events = eventService.findByNameContainingIgnoreCase("test");
        assertEquals(1, events.size());
        assertEquals("Test Event", events.get(0).getName());
    }

    @Test
    public void testdeleteById() {
        Event event = new Event();
        event.setId(1L);
        event.setName("Test Event");
        // Simulate the save action (there's no need to validate the effect of save here, since the object is no longer returned by using mock after deleteById).
        eventRepository.save(event);

        eventService.deleteById(1L);

        verify(eventRepository, times(1)).deleteById(1L);// Assume it worked due to I can not get the status from mock
    }

    @Test
    public void testFindTop3EventsWithMoreThanThreeUpcoming() {
        LocalDate now = LocalDate.now();

        Event pastEvent = new Event();
        pastEvent.setName("Past Event");
        pastEvent.setDate(now.minusDays(1));

        Event future1 = new Event();
        future1.setName("Future Event A");
        future1.setDate(now.plusDays(1));

        Event future2 = new Event();
        future2.setName("Future Event B");
        future2.setDate(now.plusDays(2));

        Event future3 = new Event();
        future3.setName("Future Event C");
        future3.setDate(now.plusDays(3));

        Event future4 = new Event();
        future4.setName("Future Event D");
        future4.setDate(now.plusDays(4));

        when(eventRepository.findUpcoming(eq(now), any(Pageable.class)))
            .thenReturn(Arrays.asList(future1, future2, future3));

        List<Event> top3 = eventService.findTop3Events();
        assertEquals(3, top3.size(), "Three future events should be returned");
        assertEquals("Future Event A", top3.get(0).getName());
        assertEquals("Future Event B", top3.get(1).getName());
        assertEquals("Future Event C", top3.get(2).getName());
        verify(eventRepository).findUpcoming(eq(now), argThat(page -> page.getPageNumber() == 0
            && page.getPageSize() == 3));
        verify(eventRepository, never()).findAllByOrderByDateAscNameAsc();
    }


    @Test
    public void testFindTop3EventsWithFewerThanThreeUpcoming() {
        LocalDate now = LocalDate.now();

        Event pastEvent = new Event();
        pastEvent.setName("Past Event");
        pastEvent.setDate(now.minusDays(2));

        Event future1 = new Event();
        future1.setName("Future Event A");
        future1.setDate(now.plusDays(1));

        when(eventRepository.findUpcoming(eq(now), any(Pageable.class))).thenReturn(Arrays.asList(future1));

        List<Event> topEvents = eventService.findTop3Events();
        assertEquals(1, topEvents.size(), "1 future event should be returned");
        assertEquals("Future Event A", topEvents.get(0).getName());
    }

    @Test
    public void todayEventsRemainUpcomingAndSameDateUsesTimeOrdering() {
        LocalDate today = LocalDate.now();
        Event late = new Event();
        late.setName("A Late");
        late.setDate(today);
        late.setTime(LocalTime.of(18, 0));
        Event early = new Event();
        early.setName("Z Early");
        early.setDate(today);
        early.setTime(LocalTime.of(9, 0));
        when(eventRepository.findUpcoming(eq(today), any(Pageable.class))).thenReturn(Arrays.asList(early, late));
        assertEquals(Arrays.asList(early, late), eventService.findTop3Events());
    }

    @Test
    public void testListEventsOrdering() throws Exception {
        Venue dummyVenue = new Venue();
        dummyVenue.setName("Dummy Venue");

        Event eventAlpha = new Event();
        eventAlpha.setId(1L);
        eventAlpha.setName("Event Alpha");
        eventAlpha.setDate(LocalDate.now().plusDays(1));
        eventAlpha.setTime(LocalTime.of(12, 30));
        eventAlpha.setVenue(dummyVenue);

        Event eventBeta = new Event();
        eventBeta.setId(2L);
        eventBeta.setName("Event Beta");
        eventBeta.setDate(LocalDate.now().plusDays(1));
        eventBeta.setTime(LocalTime.of(10, 0));
        eventBeta.setVenue(dummyVenue);

        Event eventApple = new Event();
        eventApple.setId(3L);
        eventApple.setName("Event Apple");
        eventApple.setDate(LocalDate.now().plusDays(2));
        eventApple.setTime(null);
        eventApple.setVenue(dummyVenue);

        Event eventPast = new Event();
        eventPast.setId(4L);
        eventPast.setName("Event Past");
        eventPast.setDate(LocalDate.now().minusDays(3));
        eventPast.setTime(LocalTime.of(17, 0));
        eventPast.setVenue(dummyVenue);

        Event eventFormer = new Event();
        eventFormer.setId(5L);
        eventFormer.setName("Event Former");
        eventFormer.setDate(LocalDate.now().minusDays(2));
        eventFormer.setTime(LocalTime.of(11, 0));
        eventFormer.setVenue(dummyVenue);

        Event eventPrevious = new Event();
        eventPrevious.setId(6L);
        eventPrevious.setName("Event Previous");
        eventPrevious.setDate(LocalDate.now().minusDays(2));
        eventPrevious.setTime(LocalTime.of(18, 30));
        eventPrevious.setVenue(dummyVenue);

        when(eventRepository.findAllByOrderByDateAscNameAsc()).thenReturn(Arrays.asList(
            eventAlpha, eventBeta, eventApple, eventPast, eventFormer, eventPrevious
        ));

        Iterable<Event> events = eventService.findAll();

        assertEquals(Arrays.asList(eventAlpha, eventBeta, eventApple, eventPast, eventFormer, eventPrevious), events);
    }
}
