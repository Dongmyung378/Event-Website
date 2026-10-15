package uk.ac.man.cs.eventlite.controllers;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.*;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.security.test.context.support.WithMockUser;

import uk.ac.man.cs.eventlite.config.Security;
import uk.ac.man.cs.eventlite.services.MutationService;
import uk.ac.man.cs.eventlite.exceptions.EventNotFoundException;
import uk.ac.man.cs.eventlite.exceptions.VenueNotFoundException;
import uk.ac.man.cs.eventlite.dao.VenueService;
import uk.ac.man.cs.eventlite.dao.EventService;
import uk.ac.man.cs.eventlite.entities.Venue;
import uk.ac.man.cs.eventlite.entities.Event;

@ExtendWith(SpringExtension.class)
@WebMvcTest(VenuesController.class)
@Import(Security.class)
@WithMockUser(roles = "ADMINISTRATOR")
public class VenuesControllerTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mvc;

    @MockitoBean
    private VenueService venueService;

    @MockitoBean
    private EventService eventService;

    @MockitoBean
    private MutationService mutationService;

    @Test
    void testLatitudeAndLongitude() {
        Venue venue = new Venue();

        venue.setLatitude(53.472);
        venue.setLongitude(-2.238);

        assertEquals(53.472, venue.getLatitude());
        assertEquals(-2.238, venue.getLongitude());
    }

    @Test
    public void testGetVenueDetails() throws Exception {
        Venue venue = new Venue();
        venue.setId(1L);
        venue.setName("Unit Venue");
        venue.setCapacity(100);
        venue.setAddress("Unit Road", "UT1 1AA");

        when(venueService.findById(1L)).thenReturn(Optional.of(venue));

        mvc.perform(get("/venues/1").accept(MediaType.TEXT_HTML))
            .andExpect(status().isOk())
            .andExpect(view().name("venues/details"))
            .andExpect(model().attribute("venue", is(venue)));

        verify(venueService).findById(1L);
    }

    @Test
    public void testGetVenueNotFound() throws Exception {
        when(venueService.findById(99L)).thenReturn(Optional.empty());

        mvc.perform(get("/venues/99").accept(MediaType.TEXT_HTML))
            .andExpect(status().isNotFound())
            .andExpect(view().name("venues/not_found"))
            .andExpect(model().attributeExists("not_found_id"));

        verify(venueService).findById(99L);
    }

    @Test
    public void testGetAllVenues() throws Exception {
        Venue venue1 = new Venue();
        venue1.setId(1L);
        venue1.setName("Venue One");

        Venue venue2 = new Venue();
        venue2.setId(2L);
        venue2.setName("Venue Two");

        when(venueService.findAll()).thenReturn(Arrays.asList(venue1, venue2));

        mvc.perform(get("/venues").accept(MediaType.TEXT_HTML))
            .andExpect(status().isOk())
            .andExpect(view().name("venues/index"))
            .andExpect(model().attributeExists("venues"));

        verify(venueService).findAll();
    }

    @Test
    public void testNewVenueForm() throws Exception {
        mvc.perform(get("/venues/new"))
            .andExpect(status().isOk())
            .andExpect(view().name("venues/new"))
            .andExpect(model().attributeExists("venue"));
    }


    @Test
    public void testCreateVenueValidData() throws Exception {
        mvc.perform(post("/venues")
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", "New Unit Venue")
                .param("capacity", "150")
                .param("road", "New Road")
                .param("postcode", "NU1 1AB"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/venues"))
            .andExpect(flash().attribute("ok_message", "New venue added."));

        verify(mutationService).createVenue(any(Venue.class));
    }

    @Test
    public void testCreateVenueMissingName() throws Exception {
        // Missing required field "name"
        mvc.perform(post("/venues")
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("capacity", "150")
                .param("road", "New Road")
                .param("postcode", "NU1 1AB"))
            .andExpect(status().isOk())
            .andExpect(view().name("venues/new"))
            .andExpect(model().attributeHasFieldErrors("venue", "name"));

        verify(mutationService, never()).createVenue(any(Venue.class));
    }

    @Test
    public void testCreateVenueWithLongName() throws Exception {
        String longName = "A".repeat(256);

        mvc.perform(post("/venues")
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", longName)
                .param("capacity", "150")
                .param("road", "Valid Road")
                .param("postcode", "VLD 123"))
            .andExpect(status().isOk())
            .andExpect(view().name("venues/new"))
            .andExpect(model().attributeHasFieldErrors("venue", "name"));

        verify(mutationService, never()).createVenue(any(Venue.class));
    }

    @Test
    public void testCreateVenueWithEmptyRoad() throws Exception {
        mvc.perform(post("/venues")
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", "Valid Name")
                .param("capacity", "150")
                .param("road", "")
                .param("postcode", "VLD123"))
            .andExpect(status().isOk())
            .andExpect(view().name("venues/new"))
            .andExpect(model().attributeHasFieldErrors("venue", "road"));

        verify(mutationService, never()).createVenue(any(Venue.class));
    }

    @Test
    public void testCreateVenueWithlongRoad() throws Exception {
        String longRoad = "A".repeat(300);

        mvc.perform(post("/venues")
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", "Valid Name")
                .param("capacity", "150")
                .param("road", longRoad)
                .param("postcode", "VLD123"))
            .andExpect(status().isOk())
            .andExpect(view().name("venues/new"))
            .andExpect(model().attributeHasFieldErrors("venue", "road"));

        verify(mutationService, never()).createVenue(any(Venue.class));
    }

    @Test
    public void testCreateVenueWithNoPostcode() throws Exception {
        mvc.perform(post("/venues")
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", "Valid Name")
                .param("capacity", "150")
                .param("road", "Valid Road")
                .param("postcode", ""))
            .andExpect(status().isOk())
            .andExpect(view().name("venues/new"))
            .andExpect(model().attributeHasFieldErrors("venue", "postcode"));

        verify(mutationService, never()).createVenue(any(Venue.class));
    }

    @Test
    public void testCreateVenueWithEmptyCapacity() throws Exception {
        mvc.perform(post("/venues")
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", "Valid Name")
                .param("capacity", "")
                .param("road", "Valid Road")
                .param("postcode", "M1"))
            .andExpect(status().isOk())
            .andExpect(view().name("venues/new"))
            .andExpect(model().attributeHasFieldErrors("venue", "capacity"));

        verify(mutationService, never()).createVenue(any(Venue.class));
    }

    @Test
    public void testUpdateVenueValidData() throws Exception {
        Venue existingVenue = new Venue();
        existingVenue.setId(1L);
        existingVenue.setName("Old Name");
        existingVenue.setCapacity(100);
        existingVenue.setAddress("Old Road", "OLD 1AB");

        when(venueService.findById(1L)).thenReturn(Optional.of(existingVenue));

        mvc.perform(post("/venues/updateSave/1").param("version", "0")
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", "Updated Venue")
                .param("capacity", "200")
                .param("road", "Updated Road")
                .param("postcode", "UPD1 1D"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/venues"))
            .andExpect(flash().attribute("ok_message", "Venue updated successfully."));

        verify(mutationService).updateVenue(eq(1L), any(Venue.class));
    }

    @Test
    public void testUpdateVenueWithEmptyName() throws Exception {
        Venue existingVenue = new Venue();
        existingVenue.setId(1L);
        existingVenue.setName("Old Name");
        existingVenue.setCapacity(100);
        existingVenue.setAddress("Old Road", "OLD 1AB");

        when(venueService.findById(1L)).thenReturn(Optional.of(existingVenue));

        mvc.perform(post("/venues/updateSave/1").param("version", "0")
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", "")
                .param("capacity", "1000")
                .param("road", "Updated Road")
                .param("postcode", "UPD1 1D"))
            .andExpect(status().isOk())
            .andExpect(view().name("venues/update"))
            .andExpect(model().attributeHasFieldErrors("venue", "name"));

        verify(mutationService, never()).updateVenue(anyLong(), any(Venue.class));
    }

    @Test
    public void testUpdateVenueWithLongName() throws Exception {
        Venue existingVenue = new Venue();
        existingVenue.setId(1L);
        existingVenue.setName("Old Name");
        existingVenue.setCapacity(100);
        existingVenue.setAddress("Old Road", "OLD 1AB");

        String longName = "A".repeat(257);
        when(venueService.findById(1L)).thenReturn(Optional.of(existingVenue));

        mvc.perform(post("/venues/updateSave/1").param("version", "0")
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", longName)
                .param("capacity", "1000")
                .param("road", "Updated Road")
                .param("postcode", "UPD1 1D"))
            .andExpect(status().isOk())
            .andExpect(view().name("venues/update"))
            .andExpect(model().attributeHasFieldErrors("venue", "name"));

        verify(mutationService, never()).updateVenue(anyLong(), any(Venue.class));
    }

    @Test
    public void testUpdateVenueWithEmptyRoad() throws Exception {
        Venue existingVenue = new Venue();
        existingVenue.setId(1L);
        existingVenue.setName("Old Name");
        existingVenue.setCapacity(100);
        existingVenue.setAddress("Old Road", "OLD 1AB");

        when(venueService.findById(1L)).thenReturn(Optional.of(existingVenue));

        mvc.perform(post("/venues/updateSave/1").param("version", "0")
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", "New Name")
                .param("capacity", "1000")
                .param("road", "")
                .param("postcode", "UPD1 1D"))
            .andExpect(status().isOk())
            .andExpect(view().name("venues/update"))
            .andExpect(model().attributeHasFieldErrors("venue", "road"));

        verify(mutationService, never()).updateVenue(anyLong(), any(Venue.class));
    }

    @Test
    public void testUpdateVenueWithLongRoad() throws Exception {
        Venue existingVenue = new Venue();
        existingVenue.setId(1L);
        existingVenue.setName("Old Name");
        existingVenue.setCapacity(100);
        existingVenue.setAddress("Old Road", "OLD 1AB");

        String longRoad = "A".repeat(301);
        when(venueService.findById(1L)).thenReturn(Optional.of(existingVenue));

        mvc.perform(post("/venues/updateSave/1").param("version", "0")
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", "New Name")
                .param("capacity", "1000")
                .param("road", longRoad)
                .param("postcode", "UPD1 1D"))
            .andExpect(status().isOk())
            .andExpect(view().name("venues/update"))
            .andExpect(model().attributeHasFieldErrors("venue", "road"));

        verify(mutationService, never()).updateVenue(anyLong(), any(Venue.class));
    }

    @Test
    public void testUpdateVenueWithEmptyCapacity() throws Exception {
        Venue existingVenue = new Venue();
        existingVenue.setId(1L);
        existingVenue.setName("Old Name");
        existingVenue.setCapacity(100);
        existingVenue.setAddress("Old Road", "OLD 1AB");

        when(venueService.findById(1L)).thenReturn(Optional.of(existingVenue));

        mvc.perform(post("/venues/updateSave/1").param("version", "0")
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", "New Name")
                .param("capacity", "")
                .param("road", "New Road")
                .param("postcode", "UPD1 1D"))
            .andExpect(status().isOk())
            .andExpect(view().name("venues/update"))
            .andExpect(model().attributeHasFieldErrors("venue", "capacity"));

        verify(mutationService, never()).updateVenue(anyLong(), any(Venue.class));
    }

    @Test
    public void testUpdateVenueWithNegativeCapacity() throws Exception {
        Venue existingVenue = new Venue();
        existingVenue.setId(1L);
        existingVenue.setName("Old Name");
        existingVenue.setCapacity(100);
        existingVenue.setAddress("Old Road", "OLD 1AB");

        when(venueService.findById(1L)).thenReturn(Optional.of(existingVenue));

        mvc.perform(post("/venues/updateSave/1").param("version", "0")
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", "New Name")
                .param("capacity", "-1")
                .param("road", "New Road")
                .param("postcode", "UPD1 1D"))
            .andExpect(status().isOk())
            .andExpect(view().name("venues/update"))
            .andExpect(model().attributeHasFieldErrors("venue", "capacity"));

        verify(mutationService, never()).updateVenue(anyLong(), any(Venue.class));
    }


    @Test
    public void testUpdateVenueWithEmptyPostcode() throws Exception {
        Venue existingVenue = new Venue();
        existingVenue.setId(1L);
        existingVenue.setName("Old Name");
        existingVenue.setCapacity(100);
        existingVenue.setAddress("Old Road", "OLD 1AB");

        when(venueService.findById(1L)).thenReturn(Optional.of(existingVenue));

        mvc.perform(post("/venues/updateSave/1").param("version", "0")
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", "New Name")
                .param("capacity", "1000")
                .param("road", "New Road")
                .param("postcode", ""))
            .andExpect(status().isOk())
            .andExpect(view().name("venues/update"))
            .andExpect(model().attributeHasFieldErrors("venue", "postcode"));

        verify(mutationService, never()).updateVenue(anyLong(), any(Venue.class));
    }

    @Test
    public void testDeleteVenueWithEvents() throws Exception {
        Venue venue = new Venue();
        venue.setId(1L);
        venue.setName("Venue with Events");
        // Simulate that the venue has associated events
        venue.getEvents().add(new uk.ac.man.cs.eventlite.entities.Event());

        when(venueService.existsById(1L)).thenReturn(true);
        when(venueService.findById(1L)).thenReturn(Optional.of(venue));

        mvc.perform(delete("/venues/1").with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/venues/1"))
            .andExpect(flash().attributeExists("error_message"));

        // The venue should not be deleted if it has events.
        verify(venueService, never()).deleteById(1L);
    }

    @Test
    public void testDeleteVenueSuccess() throws Exception {
        Venue venue = new Venue();
        venue.setId(2L);
        venue.setName("Venue without Events");
        // Ensure the venue has no events.
        venue.getEvents().clear();

        when(venueService.existsById(2L)).thenReturn(true);
        when(venueService.findById(2L)).thenReturn(Optional.of(venue));

        when(mutationService.deleteVenue(2L)).thenReturn(true);
        mvc.perform(delete("/venues/2").with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/venues"))
            .andExpect(flash().attribute("ok_message", "Venue deleted."));

        verify(mutationService).deleteVenue(2L);
    }

 // Additional tests to add in VenuesControllerTest.java
    @Test
    public void testSearchVenues() throws Exception {
        Venue venue = new Venue();
        venue.setId(1L);
        venue.setName("Test Venue");
        venue.setCapacity(100);
        venue.setAddress("Test Road", "TV1 1AA");

        // When searching for "test", return the dummy venue.
        when(venueService.findByNameContainingIgnoreCase("test"))
            .thenReturn(Collections.singletonList(venue));

        mvc.perform(get("/venues/search")
                .param("q", "test")
                .accept(MediaType.TEXT_HTML))
            .andExpect(status().isOk())
            .andExpect(view().name("venues/index"))
            .andExpect(model().attributeExists("venues"))
            .andExpect(model().attribute("venues",
                hasItem(hasProperty("name", equalTo("Test Venue")))));
    }

    @Test
    public void testUpdateVenueForm() throws Exception {
        Venue venue = new Venue();
        venue.setId(1L);
        venue.setName("Test Venue");
        venue.setCapacity(100);
        venue.setAddress("Test Road", "TV1 1AA");

        when(venueService.findById(1L)).thenReturn(Optional.of(venue));

        mvc.perform(get("/venues/update/1"))
            .andExpect(status().isOk())
            .andExpect(view().name("venues/update"))
            .andExpect(model().attribute("venue", venue));

        verify(venueService).findById(1L);
    }

    // Test for updateSave success (valid data)
    @Test
    public void testUpdateVenueSaveSuccess() throws Exception {
        Venue existingVenue = new Venue();
        existingVenue.setId(1L);
        existingVenue.setName("Old Venue");
        existingVenue.setCapacity(100);
        existingVenue.setAddress("Old Road", "OD1 1AA");

        when(venueService.findById(1L)).thenReturn(Optional.of(existingVenue));

        mvc.perform(post("/venues/updateSave/1").param("version", "0")
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", "Updated Venue")
                .param("capacity", "150")
                .param("road", "Updated Road")
                .param("postcode", "UPD1 1A"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/venues"))
            .andExpect(flash().attribute("ok_message", "Venue updated successfully."));

        verify(mutationService).updateVenue(eq(1L), any(Venue.class));
    }

    // Test for updateSave failure (invalid data triggering validation errors)
    @Test
    public void testUpdateVenueSaveValidationFailure() throws Exception {
        Venue existingVenue = new Venue();
        existingVenue.setId(1L);
        existingVenue.setName("Old Venue");
        existingVenue.setCapacity(100);
        existingVenue.setAddress("Old Road", "OD1 1AA");

        when(venueService.findById(1L)).thenReturn(Optional.of(existingVenue));

        mvc.perform(post("/venues/updateSave/1").param("version", "0")
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                // Provide invalid data: blank name to trigger a validation error
                .param("name", "")
                .param("capacity", "150")
                .param("road", "Updated Road")
                .param("postcode", "UPD1 1AA"))
            .andExpect(status().isOk())
            .andExpect(view().name("venues/update"))
            .andExpect(model().attributeHasFieldErrors("venue", "name"));

        // Verify that the service is not called to save an invalid venue.
        verify(mutationService, never()).updateVenue(eq(1L), any(Venue.class));
    }

    @Test
    public void testDeleteVenueNotFound() throws Exception {
        // Simulate that no venue exists with id 99
        when(venueService.existsById(99L)).thenReturn(false);

        doThrow(new VenueNotFoundException(99L)).when(mutationService).deleteVenue(99L);
        mvc.perform(delete("/venues/99").with(csrf()))
           .andExpect(status().isNotFound())
           .andExpect(view().name("venues/not_found"));

        // Ensure that the delete operation is never attempted
        verify(venueService, never()).deleteById(99L);
    }

    @Test
    public void testGetVenueEvents() throws Exception {
        Venue venue = new Venue();
        venue.setId(1L);
        venue.setName("test venue");
        Event event1 = new Event();
        Event event2 = new Event();
        event1.setId(1L);
        event2.setId(2L);
        event1.setName("Test1");
        event2.setName("Test2");
        event1.setDate(LocalDate.now().plusDays(1));
        event2.setDate(LocalDate.now().plusDays(2));
        event1.setVenue(venue);
        event2.setVenue(venue);

        List<Event> events = Arrays.asList(event1, event2);

        when(venueService.findById(1L)).thenReturn(Optional.of(venue));
        when(eventService.getEventsByVenueId(1L)).thenReturn(events);

        mvc.perform(get("/venues/1/events"))
            .andExpect(status().isOk())
            .andExpect(view().name("venues/events"))
            .andExpect(model().attribute("venue", venue))
            .andExpect(model().attribute("events", events));

        verify(venueService, times(1)).findById(1L);
        verify(eventService, times(1)).getEventsByVenueId(1L);
    }

    @Test
    public void testGetVenueEventsVenueNotfound() throws Exception {
        when(venueService.findById(99L)).thenReturn(Optional.empty());

        mvc.perform(get("/venues/99/events"))
            .andExpect(status().isNotFound())
            .andExpect(view().name("venues/not_found"));

        verify(venueService, times(1)).findById(99L);
    }

    @Test
    public void testGetVenueNext3evnets() throws Exception {
        Venue venue = new Venue();
        venue.setId(1L);
        venue.setName("test venue");

        Event event1 = new Event();
        Event event2 = new Event();
        Event event3 = new Event();
        Event event4 = new Event();

        event1.setId(1L);
        event2.setId(2L);
        event3.setId(3L);
        event3.setId(4L);

        event1.setName("Test1");
        event2.setName("Test2");
        event3.setName("Test3");
        event3.setName("Test4");

        event1.setDate(LocalDate.now().plusDays(1));
        event2.setDate(LocalDate.now().plusDays(2));
        event3.setDate(LocalDate.now().plusDays(3));
        event4.setDate(LocalDate.now().minusDays(1));

        event1.setVenue(venue);
        event2.setVenue(venue);
        event3.setVenue(venue);
        event4.setVenue(venue);

        List<Event> mixedEvents = Arrays.asList(event1, event2, event3, event4);

        List<Event> filtered = mixedEvents.stream()
                .filter(e -> e.getDate() != null && !e.getDate().isBefore(LocalDate.now()))
                .collect(Collectors.toList());

        when(venueService.findById(1L)).thenReturn(Optional.of(venue));
        when(venueService.findNext3Events(1L)).thenReturn(filtered);

        mvc.perform(get("/venues/1/next3events"))
            .andExpect(status().isOk())
            .andExpect(view().name("venues/next3events"))
            .andExpect(model().attribute("venue", venue))
            .andExpect(model().attribute("events", hasSize(3)))
            .andExpect(model().attribute("events", filtered));

        verify(venueService, times(1)).findById(1L);
        verify(venueService).findNext3Events(1L);
    }

    @Test
    public void testGetVenuenext3eventsVenueNotfound() throws Exception {
        when(venueService.findById(99L)).thenReturn(Optional.empty());

        mvc.perform(get("/venues/99/next3events"))
            .andExpect(status().isNotFound())
            .andExpect(view().name("venues/not_found"));

        verify(venueService, times(1)).findById(99L);
    }


    @Test
    public void testNewVenueFormWithFlashAttribute() throws Exception {
        Venue preExistingVenue = new Venue();
        preExistingVenue.setName("Flash Venue");
        preExistingVenue.setCapacity(50);
        preExistingVenue.setAddress("Flash Road", "FL1 1SH");

        mvc.perform(get("/venues/new").flashAttr("venue", preExistingVenue))
           .andExpect(status().isOk())
           .andExpect(view().name("venues/new"))
           .andExpect(model().attribute("venue", preExistingVenue));
    }


    @Test
    public void testUpdateSaveVenueNotFound() throws Exception {
        when(venueService.findById(99L)).thenReturn(Optional.empty());

        when(mutationService.updateVenue(eq(99L), any(Venue.class))).thenThrow(new VenueNotFoundException(99L));
        mvc.perform(post("/venues/updateSave/99").param("version", "0")
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", "Updated Venue")
                .param("capacity", "150")
                .param("road", "Updated Road")
                .param("postcode", "UP1 1AA"))
           .andExpect(status().isNotFound())
           .andExpect(view().name("venues/not_found"))
            .andExpect(model().attributeExists("not_found_id"));
    }

    @Test
    public void updateFormForMissingVenueReturns404() throws Exception {
        mvc.perform(get("/venues/update/99"))
            .andExpect(status().isNotFound())
            .andExpect(view().name("venues/not_found"));
    }

    @Test
    public void updateUsesUrlIdAndRejectsCoordinateMassAssignment() throws Exception {
        Venue existing = new Venue();
        existing.setId(1L);
        existing.setName("Venue");
        existing.setCapacity(100);
        existing.setAddress("Old Road", "OLD");
        existing.setLatitude(53.48);
        existing.setLongitude(-2.24);
        when(venueService.findById(1L)).thenReturn(Optional.of(existing));
        mvc.perform(post("/venues/updateSave/1").param("version", "0").with(csrf())
                .param("id", "99").param("name", "Venue").param("capacity", "100")
                .param("road", "New Road").param("postcode", "NEW").param("latitude", "99").param("longitude", "99"))
            .andExpect(status().is3xxRedirection());
        ArgumentCaptor<Venue> submitted = ArgumentCaptor.forClass(Venue.class);
        verify(mutationService).updateVenue(eq(1L), submitted.capture());
        org.junit.jupiter.api.Assertions.assertEquals(1L, submitted.getValue().getId());
        org.junit.jupiter.api.Assertions.assertNull(submitted.getValue().getLatitude());
        org.junit.jupiter.api.Assertions.assertNull(submitted.getValue().getLongitude());
    }
    @Test
    public void staleVenueUpdateReturnsConflict() throws Exception {
        when(mutationService.updateVenue(eq(1L), any(Venue.class)))
            .thenThrow(new uk.ac.man.cs.eventlite.exceptions.MutationConflictException("This record has changed. Reload the latest version."));
        mvc.perform(post("/venues/updateSave/1").with(csrf())
                .param("version", "4").param("name", "Stale venue").param("capacity", "100")
                .param("road", "Test Road").param("postcode", "M1 1AA"))
            .andExpect(status().isConflict())
            .andExpect(view().name("mutation-conflict"))
            .andExpect(model().attribute("reloadPath", "/venues/update/1"));
        ArgumentCaptor<Venue> form = ArgumentCaptor.forClass(Venue.class);
        verify(mutationService).updateVenue(eq(1L), form.capture());
        assertEquals(4L, form.getValue().getVersion());
        verify(mutationService, never()).createVenue(any());
    }

    @Test
    public void optionalVenuePaginationKeepsSearchQuery() throws Exception {
        when(venueService.findPage("hall", 1, 2)).thenReturn(new org.springframework.data.domain.PageImpl<>(
            Collections.emptyList(), org.springframework.data.domain.PageRequest.of(1, 2), 7));
        mvc.perform(get("/venues/search").param("q", " hall ").param("page", "1").param("size", "2"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("query", "hall"))
            .andExpect(model().attributeExists("resultPage"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(containsString("page=2")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(containsString("q=hall")));
        verify(venueService).findPage("hall", 1, 2);
        verify(venueService, never()).findAll();
    }

    @Test
    public void invalidVenuePageSizeIsABadRequest() throws Exception {
        when(venueService.findPage(null, 0, 101)).thenThrow(new IllegalArgumentException("Invalid page size"));
        mvc.perform(get("/venues").param("size", "101"))
            .andExpect(status().isBadRequest());
    }
}
