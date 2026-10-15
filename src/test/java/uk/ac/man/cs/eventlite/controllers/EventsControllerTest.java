package uk.ac.man.cs.eventlite.controllers;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

import uk.ac.man.cs.eventlite.config.Security;
import uk.ac.man.cs.eventlite.services.MutationService;
import uk.ac.man.cs.eventlite.exceptions.EventNotFoundException;
import uk.ac.man.cs.eventlite.exceptions.VenueNotFoundException;
import uk.ac.man.cs.eventlite.dao.EventService;
import uk.ac.man.cs.eventlite.dao.MastodonService;
import uk.ac.man.cs.eventlite.dao.VenueService;
import uk.ac.man.cs.eventlite.entities.Event;
import uk.ac.man.cs.eventlite.entities.Venue;

@ExtendWith({SpringExtension.class, MockitoExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
@WebMvcTest(EventsController.class)
@Import(Security.class)
@WithMockUser(roles = "ADMINISTRATOR")
public class EventsControllerTest {

    @Autowired
    private MockMvc mvc;

    @Mock
    private Event event;

    @Mock
    private Venue venue;

    @MockitoBean
    private EventService eventService;

    @MockitoBean
    private VenueService venueService;

    @MockitoBean
    private MastodonService mastodonService;

    @MockitoBean
    private MutationService mutationService;

    @BeforeEach
    public void resolveExistingVenue() {
        Venue selected = new Venue();
        selected.setId(1L);
        selected.setName("Selected Venue");
        when(venueService.findById(1L)).thenReturn(Optional.of(selected));
    }


    @Test
    public void getIndexWhenNoEvents() throws Exception {
        when(eventService.findAll()).thenReturn(Collections.emptyList());

        mvc.perform(get("/events").accept(MediaType.TEXT_HTML))
            .andExpect(status().isOk())
            .andExpect(view().name("events/index"))
            .andExpect(handler().methodName("getAllEvents"));

        verify(eventService).findAll();
        verifyNoInteractions(event);
        verifyNoInteractions(venue);
    }

    @Test
    public void getIndexWithEvents() throws Exception {
        when(event.getVenue()).thenReturn(venue);
        when(eventService.findAll()).thenReturn(Collections.singletonList(event));

        mvc.perform(get("/events").accept(MediaType.TEXT_HTML))
            .andExpect(status().isOk())
            .andExpect(view().name("events/index"))
            .andExpect(handler().methodName("getAllEvents"));

        verify(eventService).findAll();
    }

    @Test
    public void getEventNotFound() throws Exception {
        when(eventService.findById(99L)).thenReturn(Optional.empty());

        mvc.perform(get("/events/99").accept(MediaType.TEXT_HTML))
            .andExpect(status().isNotFound())
            .andExpect(view().name("events/not_found"))
            .andExpect(handler().methodName("getEvent"));
    }


    @Test
    public void testSearchForEventApple() throws Exception {
        // Construct a non-null Venue so that the template renders correctly.
        Venue dummyVenue = new Venue();
        dummyVenue.setName("Venue A");

        Event eventApple = new Event();
        eventApple.setId(3L);
        eventApple.setName("Event Apple");
        eventApple.setVenue(dummyVenue);

        when(eventService.findByNameContainingIgnoreCase("apple"))
            .thenReturn(Arrays.asList(eventApple));

        mvc.perform(get("/events/search")
                .param("q", "apple")
                .accept(MediaType.TEXT_HTML))
            .andExpect(status().isOk())
            .andExpect(model().attributeExists("events"))
            .andExpect(model().attribute("events", hasItem(hasProperty("name", equalTo("Event Apple")))));
    }

    @Test
    public void testSearchForEvent() throws Exception {
        Venue dummyVenue = new Venue();
        dummyVenue.setName("Dummy Venue");

        Event eventAlpha = new Event();
        eventAlpha.setId(1L);
        eventAlpha.setName("Event Alpha");
        eventAlpha.setVenue(dummyVenue);

        Event eventBeta = new Event();
        eventBeta.setId(2L);
        eventBeta.setName("Event Beta");
        eventBeta.setVenue(dummyVenue);

        Event eventApple = new Event();
        eventApple.setId(3L);
        eventApple.setName("Event Apple");
        eventApple.setVenue(dummyVenue);

        Event eventFormer = new Event();
        eventFormer.setId(4L);
        eventFormer.setName("Event Former");
        eventFormer.setVenue(dummyVenue);

        Event eventPrevious = new Event();
        eventPrevious.setId(5L);
        eventPrevious.setName("Event Previous");
        eventPrevious.setVenue(dummyVenue);

        Event eventPast = new Event();
        eventPast.setId(6L);
        eventPast.setName("Event Past");
        eventPast.setVenue(dummyVenue);

        when(eventService.findByNameContainingIgnoreCase("event"))
            .thenReturn(Arrays.asList(eventAlpha, eventBeta, eventApple, eventFormer, eventPrevious, eventPast));

        mvc.perform(get("/events/search")
                .param("q", "event")
                .accept(MediaType.TEXT_HTML))
            .andExpect(status().isOk())
            .andExpect(model().attributeExists("events"))
            .andExpect(model().attribute("events", hasItem(hasProperty("name", equalTo("Event Alpha")))))
            .andExpect(model().attribute("events", hasItem(hasProperty("name", equalTo("Event Beta")))))
            .andExpect(model().attribute("events", hasItem(hasProperty("name", equalTo("Event Apple")))))
            .andExpect(model().attribute("events", hasItem(hasProperty("name", equalTo("Event Former")))))
            .andExpect(model().attribute("events", hasItem(hasProperty("name", equalTo("Event Previous")))))
            .andExpect(model().attribute("events", hasItem(hasProperty("name", equalTo("Event Past")))));
    }


    @Test
    public void testGetNewEventForm() throws Exception {
        when(venueService.findAll()).thenReturn(Collections.emptyList());

        mvc.perform(get("/events/new"))
            .andExpect(status().isOk())
            .andExpect(view().name("events/new"))
            .andExpect(model().attributeExists("event"))
            .andExpect(model().attributeExists("venues"));

        verify(venueService).findAll();
        verifyNoInteractions(eventService);
    }

    @Test
    public void testCreateEventSuccess() throws Exception {
        when(venueService.findAll()).thenReturn(Collections.singletonList(venue));


        mvc.perform(post("/events")
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", "Test Event")
                .param("date", LocalDate.now().plusDays(1).toString())
                .param("description", "Description < 500 chars")
                .param("venue.id", "1")
            )
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/events"))
            .andExpect(flash().attribute("ok_message", "New event added."));

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(mutationService).createEvent(captor.capture());
        Event savedEvent = captor.getValue();
        assertTrue("Test Event".equals(savedEvent.getName()));
        assertTrue(LocalDate.now().plusDays(1).equals(savedEvent.getDate()));
    }

    @Test
    public void testCreateEventValidationFailure() throws Exception {
        when(venueService.findAll()).thenReturn(Collections.singletonList(venue));

        mvc.perform(post("/events")
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", "Invalid Event")
                .param("date", "2020-01-01")
            )
            .andExpect(status().isOk())
            .andExpect(view().name("events/new"))
            .andExpect(model().attributeHasFieldErrors("event", "date"))
            .andExpect(model().attributeExists("venues"));

        verifyNoInteractions(eventService);
    }

    @Test
    public void testUpdateEventFormSuccess() throws Exception {
        when(eventService.findById(1L)).thenReturn(Optional.of(event));
        when(venueService.findAll()).thenReturn(Collections.singletonList(venue));
        when(event.getId()).thenReturn(1L);

        mvc.perform(get("/events/update/1"))
            .andExpect(status().isOk())
            .andExpect(view().name("events/update"))
            .andExpect(model().attribute("event", is(event)))
            .andExpect(model().attributeExists("venues"));

        verify(eventService).findById(1L);
        verify(venueService).findAll();
    }

    @Test
    public void testUpdateEventFormNotFound() throws Exception {
        when(eventService.findById(99L)).thenReturn(Optional.empty());

        mvc.perform(get("/events/update/99"))
            .andExpect(status().isNotFound())
            .andExpect(view().name("events/not_found"));
    }

    @Test
    public void testUpdateEventSaveSuccess() throws Exception {
        when(eventService.findById(1L)).thenReturn(Optional.of(event));
        when(venueService.findAll()).thenReturn(Collections.singletonList(venue));

        mvc.perform(post("/events/updateSave/1").param("version", "0")
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("id", "1")
                .param("name", "Updated Event")
                .param("date", LocalDate.now().plusDays(1).toString())
                .param("venue.id", "1")
            )
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/events"))
            .andExpect(flash().attribute("ok_message", "Event updated successfully."));

        verify(mutationService).updateEvent(eq(1L), any(Event.class));
    }

    @Test
    public void testUpdateEventSaveValidationFailure() throws Exception {
        when(eventService.findById(1L)).thenReturn(Optional.of(event));
        when(venueService.findAll()).thenReturn(Collections.singletonList(venue));

        mvc.perform(post("/events/updateSave/1").param("version", "0")
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", "Updated Event")
                .param("date", "2000-01-01")
                .param("venue.id", "1")
            )
            .andExpect(status().isOk())
            .andExpect(view().name("events/update"))
            .andExpect(model().attributeHasFieldErrors("event", "date"));

        verify(mutationService, never()).updateEvent(anyLong(), any(Event.class));
    }

    @Test
    public void testDeleteEventSuccess() throws Exception {
        when(eventService.existsById(1L)).thenReturn(true);

        mvc.perform(delete("/events/1")
                .with(csrf())
            )
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/events"))
            .andExpect(flash().attribute("ok_message", "Event deleted."));

        verify(mutationService).deleteEvent(1L);
    }

    @Test
    public void testDeleteEventNotFound() throws Exception {
        when(eventService.existsById(99L)).thenReturn(false);

        doThrow(new EventNotFoundException(99L)).when(mutationService).deleteEvent(99L);
        mvc.perform(delete("/events/99")
                .with(csrf())
            )
            .andExpect(status().isNotFound())
            .andExpect(view().name("events/not_found"));

        verify(eventService, never()).deleteById(99L);
    }

    @Test
    public void testGetEventDetailsSuccess() throws Exception {
        when(eventService.findById(5L)).thenReturn(Optional.of(event));
        when(event.getName()).thenReturn("Some Event");

        mvc.perform(get("/events/5"))
            .andExpect(status().isOk())
            .andExpect(view().name("events/details"))
            .andExpect(model().attribute("event", is(event)));

        verify(eventService).findById(5L);
    }

    @Test
    public void testGetEventVenueNotfoundEvent() throws Exception {
        when(eventService.findById(99L)).thenReturn(Optional.empty());

        mvc.perform(get("/events/99/venue"))
        .andExpect(status().isNotFound())
        .andExpect(view().name("events/not_found"));

        verify(eventService).findById(99L);
    }

    @Test
    public void testGetEventVenueNotfoundVenue() throws Exception {
		 Event eventWithoutVenue = new Event();
        eventWithoutVenue.setId(5L);
        eventWithoutVenue.setName("Test Event");
        eventWithoutVenue.setVenue(null);

        when(eventService.findById(5L)).thenReturn(Optional.of(eventWithoutVenue));

        mvc.perform(get("/events/5/venue"))
            .andExpect(status().isOk())
            .andExpect(view().name("venues/not_found"))
            .andExpect(model().attributeExists("not_found_id"))
            .andExpect(model().attribute("not_found_id", 5L));

        verify(eventService).findById(5L);
    }

    @Test
    public void testGetEventVenueSuccess() throws Exception {
         venue = new Venue();
        venue.setId(1L);
        venue.setName("Test Venue");

        event = new Event();
        event.setId(5L);
        event.setName("Test Event");
        event.setVenue(venue);

        when(eventService.findById(5L)).thenReturn(Optional.of(event));

        mvc.perform(get("/events/5/venue"))
            .andExpect(status().isOk())
            .andExpect(view().name("venues/details"))
            .andExpect(model().attributeExists("venue"))
            .andExpect(model().attribute("venue", venue));

        verify(eventService).findById(5L);
    }

    @Test
    public void testGetNewEventFormWithExistingEvent() throws Exception {
        when(venueService.findAll()).thenReturn(Collections.singletonList(venue));

        Event existingEvent = new Event();
        existingEvent.setName("Existing Event");

        mvc.perform(get("/events/new")
                .flashAttr("event", existingEvent)
                .accept(MediaType.TEXT_HTML))
            .andExpect(status().isOk())
            .andExpect(view().name("events/new"))
            .andExpect(model().attribute("event", is(existingEvent)))
            .andExpect(model().attributeExists("venues"));

        verify(venueService).findAll();
        verifyNoInteractions(eventService);
    }

    @Test
    public void testCreateEventValidationFailureForLongName() throws Exception {
        when(venueService.findAll()).thenReturn(Collections.singletonList(venue));
        String tooLongName = "a".repeat(257);

        mvc.perform(post("/events")
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", tooLongName)
                .param("date", LocalDate.now().plusDays(1).toString())
                .param("description", "Valid description")
                .param("venue.id", "1")
            )
            .andExpect(status().isOk())
            .andExpect(view().name("events/new"))
            .andExpect(model().attributeHasFieldErrors("event", "name"));

        verifyNoInteractions(eventService);
    }

    @Test
    public void testCreateEventValidationFailureForLongDescription() throws Exception {
        when(venueService.findAll()).thenReturn(Collections.singletonList(venue));
        String tooLongDescription = "a".repeat(501);

        mvc.perform(post("/events")
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", "Valid Event Name")
                .param("date", LocalDate.now().plusDays(1).toString())
                .param("description", tooLongDescription)
                .param("venue.id", "1")
            )
            .andExpect(status().isOk())
            .andExpect(view().name("events/new"))
            .andExpect(model().attributeHasFieldErrors("event", "description"));

        verifyNoInteractions(eventService);
    }

    @Test
    public void testCreateEventSuccessForNameLength255() throws Exception {
        when(venueService.findAll()).thenReturn(Collections.singletonList(venue));


        // Create a valid name with exactly 255 characters.
        String validName = "a".repeat(255);

        mvc.perform(post("/events")
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", validName)
                .param("date", LocalDate.now().plusDays(1).toString())
                .param("description", "Valid description")
                .param("venue.id", "1")
            )
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/events"))
            .andExpect(flash().attribute("ok_message", "New event added."));

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(mutationService).createEvent(captor.capture());
        Event savedEvent = captor.getValue();
        assertTrue(validName.equals(savedEvent.getName()));
    }

    @Test
    public void testCreateEventSuccessForDescriptionLength499() throws Exception {
        when(venueService.findAll()).thenReturn(Collections.singletonList(venue));


        // Create a valid description with exactly 499 characters.
        String validDescription = "a".repeat(499);

        mvc.perform(post("/events")
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", "Valid Event Name")
                .param("date", LocalDate.now().plusDays(1).toString())
                .param("description", validDescription)
                .param("venue.id", "1")
            )
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/events"))
            .andExpect(flash().attribute("ok_message", "New event added."));

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(mutationService).createEvent(captor.capture());
        Event savedEvent = captor.getValue();
        assertTrue(validDescription.equals(savedEvent.getDescription()));
    }


 // Create Event: Missing Name
    @Test
    public void testCreateEventValidationFailureForMissingName() throws Exception {
        when(venueService.findAll()).thenReturn(Collections.singletonList(venue));

        mvc.perform(post("/events")
            .with(csrf())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            // Name omitted
            .param("date", LocalDate.now().plusDays(1).toString())
            .param("description", "Valid description")
            .param("venue.id", "1")
        )
        .andExpect(status().isOk())
        .andExpect(view().name("events/new"))
        .andExpect(model().attributeHasFieldErrors("event", "name"));

        verifyNoInteractions(eventService);
    }

    // Create Event: Missing Date
    @Test
    public void testCreateEventValidationFailureForMissingDate() throws Exception {
        when(venueService.findAll()).thenReturn(Collections.singletonList(venue));

        mvc.perform(post("/events")
            .with(csrf())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("name", "Test Event")
            .param("description", "Valid description")
            .param("venue.id", "1")
        )
        .andExpect(status().isOk())
        .andExpect(view().name("events/new"))
        .andExpect(model().attributeHasFieldErrors("event", "date"));

        verifyNoInteractions(eventService);
    }

    // Create Event: Missing Venue
    @Test
    public void testCreateEventValidationFailureForMissingVenue() throws Exception {
        when(venueService.findAll()).thenReturn(Collections.singletonList(venue));

        mvc.perform(post("/events")
            .with(csrf())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("name", "Test Event")
            .param("date", LocalDate.now().plusDays(1).toString())
            .param("description", "Valid description")
        )
        .andExpect(status().isOk())
        .andExpect(view().name("events/new"))
        .andExpect(model().attributeHasFieldErrors("event", "venue"));

        verifyNoInteractions(eventService);
    }

    // Update Event: Missing Name
    @Test
    public void testUpdateEventValidationFailureForMissingName() throws Exception {
        when(eventService.findById(1L)).thenReturn(Optional.of(new Event()));
        when(venueService.findAll()).thenReturn(Collections.singletonList(venue));

        mvc.perform(post("/events/updateSave/1").param("version", "0")
            .with(csrf())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("id", "1")
            .param("date", LocalDate.now().plusDays(1).toString())
            .param("venue.id", "1")
        )
        .andExpect(status().isOk())
        .andExpect(view().name("events/update"))
        .andExpect(model().attributeHasFieldErrors("event", "name"));

        verify(mutationService, never()).updateEvent(anyLong(), any(Event.class));
    }

    // Update Event: Missing Date
    @Test
    public void testUpdateEventValidationFailureForMissingDate() throws Exception {
        when(eventService.findById(1L)).thenReturn(Optional.of(new Event()));
        when(venueService.findAll()).thenReturn(Collections.singletonList(venue));

        mvc.perform(post("/events/updateSave/1").param("version", "0")
            .with(csrf())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("id", "1")
            .param("name", "Updated Event")
            .param("venue.id", "1")
        )
        .andExpect(status().isOk())
        .andExpect(view().name("events/update"))
        .andExpect(model().attributeHasFieldErrors("event", "date"));

        verify(mutationService, never()).updateEvent(anyLong(), any(Event.class));
    }

    // Update Event: Missing Venue
    @Test
    public void testUpdateEventValidationFailureForMissingVenue() throws Exception {
        when(eventService.findById(1L)).thenReturn(Optional.of(new Event()));
        when(venueService.findAll()).thenReturn(Collections.singletonList(venue));

        mvc.perform(post("/events/updateSave/1").param("version", "0")
            .with(csrf())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("id", "1")
            .param("name", "Updated Event")
            .param("date", LocalDate.now().plusDays(1).toString())
        )
        .andExpect(status().isOk())
        .andExpect(view().name("events/update"))
        .andExpect(model().attributeHasFieldErrors("event", "venue"));

        verify(mutationService, never()).updateEvent(anyLong(), any(Event.class));
    }

    @Test
    public void testPostEventSuccess() throws Exception {
        // Arrange
        Event event = new Event();
        event.setId(1L);
        event.setName("Test Event");
        when(eventService.findById(1L)).thenReturn(Optional.of(event));

        String message = "Hello, Mastodon!";
        mvc.perform(post("/events/1")
                .with(csrf())
                .param("m", message))
           .andExpect(status().isOk())
           .andExpect(view().name("events/details"))
           .andExpect(model().attribute("event", event))
           .andExpect(model().attribute("success", true))
           .andExpect(model().attribute("message", message));

        // Verify that MastodonService.postStatus() is called with the message.
        verify(mastodonService).postStatus(message);
    }

    @Test
    public void updateUsesUrlIdDespiteConflictingFormId() throws Exception {
        Event existing = new Event();
        existing.setId(1L);
        when(eventService.findById(1L)).thenReturn(Optional.of(existing));
        mvc.perform(post("/events/updateSave/1").param("version", "0").with(csrf())
                .param("id", "2").param("name", "Updated Event")
                .param("date", LocalDate.now().plusDays(1).toString()).param("venue.id", "1"))
            .andExpect(status().is3xxRedirection());
        ArgumentCaptor<Event> submitted = ArgumentCaptor.forClass(Event.class);
        verify(mutationService).updateEvent(eq(1L), submitted.capture());
        org.junit.jupiter.api.Assertions.assertEquals(1L, submitted.getValue().getId());
        org.junit.jupiter.api.Assertions.assertEquals("Updated Event", submitted.getValue().getName());
        verify(eventService, never()).findById(2L);
    }

    @Test
    public void updateMissingEventDoesNotInsert() throws Exception {
        when(mutationService.updateEvent(eq(99L), any(Event.class))).thenThrow(new EventNotFoundException(99L));
        mvc.perform(post("/events/updateSave/99").param("version", "0").with(csrf())
                .param("name", "Updated Event").param("date", LocalDate.now().plusDays(1).toString())
                .param("venue.id", "1"))
            .andExpect(status().isNotFound());
        verify(mutationService, never()).createEvent(any());
    }

    @Test
    public void postingMissingEventDoesNotCallMastodon() throws Exception {
        mvc.perform(post("/events/99").with(csrf()).param("m", "An event"))
            .andExpect(status().isNotFound());
        verify(mastodonService, never()).postStatus(anyString());
    }

    @Test
    public void invalidMessagesDoNotCallMastodon() throws Exception {
        Event existing = new Event();
        existing.setId(1L);
        when(eventService.findById(1L)).thenReturn(Optional.of(existing));
        for (String message : Arrays.asList("   ", "x".repeat(501))) {
            mvc.perform(post("/events/1").with(csrf()).param("m", message))
                .andExpect(status().isOk())
                .andExpect(model().attribute("success", false))
                .andExpect(model().attributeExists("error_message"));
        }
        verify(mastodonService, never()).postStatus(anyString());
    }

    @Test
    public void failedPostReportsFailureWithoutExceptionDetails() throws Exception {
        Event existing = new Event();
        existing.setId(1L);
        when(eventService.findById(1L)).thenReturn(Optional.of(existing));
        doThrow(new IllegalStateException("private provider response"))
            .when(mastodonService).postStatus("An event");
        mvc.perform(post("/events/1").with(csrf()).param("m", "An event"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("success", false))
            .andExpect(model().attribute("message", "An event"))
            .andExpect(model().attribute("error_message", "Social posting is unavailable. Please try again later."));
    }

    @Test
    public void testPostEventNotFound() throws Exception {
        long nonExistentId = 99L;
        when(eventService.findById(nonExistentId)).thenReturn(Optional.empty());
        String message = "Test message";

        mvc.perform(post("/events/" + nonExistentId)
                .with(csrf())
                .param("m", message))
           .andExpect(status().isNotFound());
    }

    @Test
    public void staleUpdateReturnsConflictWithoutCallingCreate() throws Exception {
        when(mutationService.updateEvent(eq(1L), any(Event.class)))
            .thenThrow(new uk.ac.man.cs.eventlite.exceptions.MutationConflictException("This record has changed. Reload the latest version."));
        mvc.perform(post("/events/updateSave/1").with(csrf())
                .param("version", "2").param("name", "Stale edit")
                .param("date", LocalDate.now().plusDays(1).toString()).param("venue.id", "1"))
            .andExpect(status().isConflict())
            .andExpect(view().name("mutation-conflict"))
            .andExpect(model().attribute("reloadPath", "/events/update/1"))
            .andExpect(model().attribute("conflictMessage", org.hamcrest.Matchers.containsString("Reload")));
        ArgumentCaptor<Event> form = ArgumentCaptor.forClass(Event.class);
        verify(mutationService).updateEvent(eq(1L), form.capture());
        org.junit.jupiter.api.Assertions.assertEquals(2L, form.getValue().getVersion());
        verify(mutationService, never()).createEvent(any());
    }

    @Test
    public void databaseConflictDoesNotExposeDatabaseDetails() throws Exception {
        when(mutationService.updateEvent(eq(1L), any(Event.class)))
            .thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException("private_table", 1L));
        mvc.perform(post("/events/updateSave/1").with(csrf())
                .param("version", "0").param("name", "Changed")
                .param("date", LocalDate.now().plusDays(1).toString()).param("venue.id", "1"))
            .andExpect(status().isConflict())
            .andExpect(model().attribute("conflictMessage", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private_table"))));
    }

    @Test
    public void optionalPaginationUsesDatabasePageAndKeepsSearchQuery() throws Exception {
        when(eventService.findPage("test", 1, 2)).thenReturn(new org.springframework.data.domain.PageImpl<>(
            Collections.emptyList(), org.springframework.data.domain.PageRequest.of(1, 2), 7));
        mvc.perform(get("/events/search").param("q", " test ").param("page", "1").param("size", "2"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("query", "test"))
            .andExpect(model().attributeExists("resultPage"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("page=2")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("q=test")));
        verify(eventService).findPage("test", 1, 2);
        verify(eventService, never()).findAll();
    }

    @Test
    public void invalidPaginationIsABadRequest() throws Exception {
        when(eventService.findPage(null, -1, 20)).thenThrow(new IllegalArgumentException("Invalid page"));
        mvc.perform(get("/events").param("page", "-1"))
            .andExpect(status().isBadRequest());
    }
}
