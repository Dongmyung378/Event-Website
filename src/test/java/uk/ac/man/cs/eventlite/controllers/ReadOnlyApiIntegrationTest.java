package uk.ac.man.cs.eventlite.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import uk.ac.man.cs.eventlite.dao.EventService;
import uk.ac.man.cs.eventlite.dao.VenueService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReadOnlyApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EventService events;
    @Autowired VenueService venues;

    @Test
    void anonymousClientsCanDiscoverReadOnlyResources() throws Exception {
        mvc.perform(get("/api").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._links.events.href").exists())
            .andExpect(jsonPath("$._links.venues.href").exists());
    }

    @Test
    void repositoryWritesRemainForbiddenEvenForAdministratorWithCsrf() throws Exception {
        long eventCount = events.count(), venueCount = venues.count();
        var administrator = user("review-admin").roles("ADMINISTRATOR");
        mvc.perform(post("/api/events").with(administrator).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"unexpected write\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(delete("/api/events/1").with(administrator).with(csrf()))
            .andExpect(status().isForbidden());
        mvc.perform(delete("/api/venues/1").with(administrator).with(csrf()))
            .andExpect(status().isForbidden());
        assertEquals(eventCount, events.count());
        assertEquals(venueCount, venues.count());
    }
}
