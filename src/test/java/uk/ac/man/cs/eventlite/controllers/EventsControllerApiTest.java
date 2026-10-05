package uk.ac.man.cs.eventlite.controllers;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import uk.ac.man.cs.eventlite.assemblers.EventModelAssembler;
import uk.ac.man.cs.eventlite.assemblers.VenueModelAssembler;
import uk.ac.man.cs.eventlite.config.Security;
import uk.ac.man.cs.eventlite.dao.EventService;
import uk.ac.man.cs.eventlite.entities.Event;
import uk.ac.man.cs.eventlite.entities.Venue;

@ExtendWith(SpringExtension.class)
@WebMvcTest(EventsControllerApi.class)
@Import({ Security.class, EventModelAssembler.class })
public class EventsControllerApiTest {

	@Autowired
	private MockMvc mvc;

	@MockBean
	private EventService eventService;

	@MockBean
	private VenueModelAssembler venueModelAssembler;

	@Test
	public void getIndexWhenNoEvents() throws Exception {
		when(eventService.findAll()).thenReturn(Collections.<Event>emptyList());

		mvc.perform(get("/api/events").accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(handler().methodName("getAllEvents")).andExpect(jsonPath("$.length()", equalTo(1)))
				.andExpect(jsonPath("$._links.self.href", endsWith("/api/events")));

		verify(eventService).findAll();
	}

	@Test
	public void getIndexWithEvents() throws Exception {
		Event e = new Event();
		e.setId(0);
		e.setName("Event");
		e.setDate(LocalDate.now());
		e.setTime(LocalTime.now());
		Venue venue = new Venue();
		venue.setId(0L);
		e.setVenue(venue);
		when(eventService.findAll()).thenReturn(Collections.<Event>singletonList(e));

		mvc.perform(get("/api/events").accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(handler().methodName("getAllEvents")).andExpect(jsonPath("$.length()", equalTo(2)))
				.andExpect(jsonPath("$._links.self.href", endsWith("/api/events")))
				.andExpect(jsonPath("$._embedded.events.length()", equalTo(1)));

		verify(eventService).findAll();
	}

	@Test
	public void getEventNotFound() throws Exception {
		mvc.perform(get("/api/events/99").accept(MediaType.APPLICATION_JSON)).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error", containsString("event 99"))).andExpect(jsonPath("$.id", equalTo(99)))
				.andExpect(handler().methodName("getEvent"));
	}

	@Test
	public void getEventFound() throws Exception {
	    Event event = new Event();
	    event.setId(1);
	    event.setName("Test Event");
	    event.setDate(LocalDate.now());
	    event.setTime(LocalTime.now());
	    Venue venue = new Venue();
	    venue.setId(1L);
	    event.setVenue(venue);

	    // Mock the service to return the event when searching by id
	    when(eventService.findById(1)).thenReturn(Optional.of(event));

	    mvc.perform(get("/api/events/1").accept(MediaType.APPLICATION_JSON))
	        .andExpect(status().isOk())
	        .andExpect(handler().methodName("getEvent"))
	        .andExpect(jsonPath("$.id", equalTo(1)))
	        .andExpect(jsonPath("$.name", equalTo("Test Event")))
	        .andExpect(jsonPath("$._links.self.href", endsWith("/api/events/1")));

	    verify(eventService).findById(1);
	}

    @Test
    void testGetEventVenue_Success() throws Exception {
        Event event = new Event();
        event.setId(1L);
        Venue venue = new Venue();
        venue.setId(100L);
        event.setVenue(venue);

        when(eventService.findById(1L)).thenReturn(Optional.of(event));
        when(venueModelAssembler.toModel(venue)).thenReturn(EntityModel.of(venue));

        mvc.perform(get("/api/events/1/venue")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100L));

        verify(eventService, times(1)).findById(1L);
        verify(venueModelAssembler, times(1)).toModel(venue);
    }


    @Test
    void testGetEventVenue_EventNotFound() throws Exception {
        when(eventService.findById(2L)).thenReturn(Optional.empty());

        mvc.perform(get("/api/events/2/venue")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.id").value(2));
        verify(eventService, times(1)).findById(2L);
    }

	@Test
    void testGetEventVenue_VenueNotFound() throws Exception {
        Event event = new Event();
        event.setId(7L);
        event.setVenue(null);

        when(eventService.findById(7L)).thenReturn(Optional.of(event));

        mvc.perform(get("/api/events/7/venue")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        verify(eventService, times(1)).findById(7L);
    }
}
