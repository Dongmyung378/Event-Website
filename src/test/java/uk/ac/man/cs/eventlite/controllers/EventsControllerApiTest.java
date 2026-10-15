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
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

	@MockitoBean
	private EventService eventService;

	@MockitoBean
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
    @Test
    void optionalPageIncludesMetadataAndNavigation() throws Exception {
        Event event = new Event();
        event.setId(12L);
        event.setName("Paged event");
        event.setVersion(8L);
        when(eventService.findPage("Event", 1, 1)).thenReturn(new org.springframework.data.domain.PageImpl<>(
            java.util.List.of(event), org.springframework.data.domain.PageRequest.of(1, 1), 3));
        mvc.perform(get("/api/events").accept(MediaType.APPLICATION_JSON).param("page", "1").param("size", "1").queryParam("q", "Event"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.number").value(1))
            .andExpect(jsonPath("$.page.totalElements").value(3))
            .andExpect(jsonPath("$._embedded.events[0].id").value(12))
            .andExpect(jsonPath("$._embedded.events[0].version").doesNotExist())
            .andExpect(jsonPath("$._links.prev.href", containsString("page=0")))
            .andExpect(jsonPath("$._links.next.href", containsString("page=2")))
            .andExpect(jsonPath("$._links.next.href", containsString("q=Event")));
    }

    @Test
    void invalidPageIsABadRequest() throws Exception {
        when(eventService.findPage(null, -1, 20)).thenThrow(new IllegalArgumentException("Invalid page"));
        mvc.perform(get("/api/events").accept(MediaType.APPLICATION_JSON).param("page", "-1"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
    }

    @Test
    void paginationLinksKeepEncodedSearchTermsWhenFollowed() throws Exception {
        Event event = new Event();
        event.setId(1L);
        event.setName("Matched event");
        com.fasterxml.jackson.databind.ObjectMapper json = new com.fasterxml.jackson.databind.ObjectMapper();
        for (String query : java.util.List.of("%", "two words", "한글 행사", "a+b", "100%_! & map")) {
            for (int page = 0; page < 2; page++) {
                when(eventService.findPage(query, page, 1)).thenReturn(new org.springframework.data.domain.PageImpl<>(
                    java.util.List.of(event), org.springframework.data.domain.PageRequest.of(page, 1), 3));
            }
            String encoded = java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
            String first = mvc.perform(get(java.net.URI.create("/api/events?page=0&size=1&q=" + encoded)).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            String next = json.readTree(first).at("/_links/next/href").asText();
            String second = mvc.perform(get(java.net.URI.create(next)).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.number").value(1))
                .andReturn().getResponse().getContentAsString();
            String previous = json.readTree(second).at("/_links/prev/href").asText();
            mvc.perform(get(java.net.URI.create(previous)).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.number").value(0));
            verify(eventService, times(2)).findPage(query, 0, 1);
            verify(eventService).findPage(query, 1, 1);
        }
    }
}
