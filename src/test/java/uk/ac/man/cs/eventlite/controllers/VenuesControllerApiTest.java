package uk.ac.man.cs.eventlite.controllers;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.springframework.hateoas.Link;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import uk.ac.man.cs.eventlite.assemblers.VenueModelAssembler;
import uk.ac.man.cs.eventlite.assemblers.EventModelAssembler;
import uk.ac.man.cs.eventlite.config.Security;
import uk.ac.man.cs.eventlite.dao.VenueService;
import uk.ac.man.cs.eventlite.dao.EventService;
import uk.ac.man.cs.eventlite.entities.Venue;
import uk.ac.man.cs.eventlite.entities.Event;
import static org.hamcrest.Matchers.*;

@ExtendWith(SpringExtension.class)
@WebMvcTest(VenuesControllerApi.class)
@Import({ Security.class, VenueModelAssembler.class })
public class VenuesControllerApiTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private VenueService venueService;

    @MockBean
    private EventService eventService;

    @Autowired
    private VenueModelAssembler venueAssembler;

    @MockBean
    private EventModelAssembler eventAssembler;

    @Test
    public void getIndexWhenNoVenues() throws Exception {
        when(venueService.findAll()).thenReturn(Collections.emptyList());
        // Stub the assembler to return an empty CollectionModel rather than null.

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/venues")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler().methodName("getAllVenues"))
            // For an empty collection, you might get only _links.
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.length()", equalTo(1)))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$._links.self.href", endsWith("/api/venues")));

        verify(venueService).findAll();
    }

    @Test
    public void getIndexWithVenues() throws Exception {
        Venue v = new Venue();
        v.setId(0L);
        v.setName("Test Venue");
        v.setCapacity(100);
        v.setAddress("Test Road", "TV1 1AA");

        // Build the EntityModel for the venue using the assembler.
        EntityModel<Venue> venueModel = EntityModel.of(v);
        when(venueService.findAll()).thenReturn(Collections.singletonList(v));


        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/venues")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler().methodName("getAllVenues"))
            // Expect two top-level keys: _links and _embedded.
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.length()", equalTo(2)))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$._links.self.href", endsWith("/api/venues")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$._embedded.venues[0].id", equalTo(0)))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$._embedded.venues[0]._links.self.href", endsWith("/api/venues/0")));
        verify(venueService).findAll();
    }


    @Test
    public void getVenueNotFound() throws Exception {
        when(venueService.findById(99L)).thenReturn(Optional.empty());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/venues/99")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error", containsString("venue 99")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.id", equalTo(99)))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler().methodName("getVenue"));
    }

    @Test
    public void getVenue() throws Exception {
        Venue venue = new Venue();
        venue.setId(1L);
        venue.setName("Test Venue");
        venue.setCapacity(100);
        venue.setAddress("Test Road", "TV1 1AA");

        // Stub the service and assembler.
        when(venueService.findById(1L)).thenReturn(Optional.of(venue));
        EntityModel<Venue> venueModel = EntityModel.of(venue);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/venues/1")
                .accept(MediaType.APPLICATION_JSON))
             .andExpect(status().isOk())
             .andExpect(jsonPath("$.name").value("Test Venue"))
             .andExpect(jsonPath("$._links.self.href", endsWith("/api/venues/1")));


        verify(venueService).findById(1L);
    }

    @Test
    public void testGetVenueEvents() throws Exception {
        Venue venue = new Venue();
        venue.setId(1L);
        venue.setName("Test Venue");

        Event event1 = new Event();
        event1.setId(1L);
        event1.setName("Test Event 1");
        event1.setDate(LocalDate.now().plusDays(1));
        event1.setVenue(venue);

        Event event2 = new Event();
        event2.setId(2L);
        event2.setName("Test Event 2");
        event2.setDate(LocalDate.now().plusDays(2));
        event2.setVenue(venue);

        Event event3 = new Event();
        event3.setId(3L);
        event3.setName("Test Event 3");
        event3.setDate(LocalDate.now().plusDays(3));
        event3.setVenue(venue);

        List<Event> events = Arrays.asList(event1, event2, event3);

        when(venueService.findById(1L)).thenReturn(Optional.of(venue));
        when(eventService.getEventsByVenueId(1L)).thenReturn(events);

        when(eventAssembler.toModel(event1)).thenReturn(EntityModel.of(event1));
        when(eventAssembler.toModel(event2)).thenReturn(EntityModel.of(event2));
        when(eventAssembler.toModel(event3)).thenReturn(EntityModel.of(event3));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/venues/1/events")
                .accept(MediaType.APPLICATION_JSON))
	        .andExpect(status().isOk());

        verify(venueService).findById(1L);
        verify(eventService).getEventsByVenueId(1L);
    }

	@Test
    void testGetVenueEvents_VenueNotFound() throws Exception {
		Venue venue = new Venue();
		venue.setId(7L);

        when(venueService.findById(7L)).thenReturn(Optional.empty());

        mvc.perform(get("/api/venues/7/events")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        verify(venueService).findById(7L);
    }

    @Test
    public void testGetVenueNext3events() throws Exception {
        Venue venue = new Venue();
        venue.setId(1L);
        venue.setName("Test Venue");

        Event event1 = new Event();
        event1.setId(1L);
        event1.setName("Test Event 1");
        event1.setDate(LocalDate.now().plusDays(1));
        event1.setVenue(venue);

        Event event2 = new Event();
        event2.setId(2L);
        event2.setName("Test Event 2");
        event2.setDate(LocalDate.now().plusDays(2));
        event2.setVenue(venue);

        Event event3 = new Event();
        event3.setId(3L);
        event3.setName("Test Event 3");
        event3.setDate(LocalDate.now().plusDays(3));
        event3.setVenue(venue);

        List<Event> events = Arrays.asList(event1, event2, event3);

        when(venueService.findById(1L)).thenReturn(Optional.of(venue));
        when(eventService.getEventsByVenueId(1L)).thenReturn(events);

        when(eventAssembler.toModel(event1)).thenReturn(EntityModel.of(event1));
        when(eventAssembler.toModel(event2)).thenReturn(EntityModel.of(event2));
        when(eventAssembler.toModel(event3)).thenReturn(EntityModel.of(event3));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/venues/1/next3events")
                .accept(MediaType.APPLICATION_JSON))
	        .andExpect(status().isOk());

        verify(venueService).findById(1L);
        verify(eventService).getEventsByVenueId(1L);
    }

	@Test
    void testGetVenuenext3events_VenueNotFound() throws Exception {
		Venue venue = new Venue();
		venue.setId(7L);

        when(venueService.findById(7L)).thenReturn(Optional.empty());

        mvc.perform(get("/api/venues/7/next3events")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        verify(venueService).findById(7L);
    }
}
