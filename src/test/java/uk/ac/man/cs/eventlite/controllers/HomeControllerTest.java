package uk.ac.man.cs.eventlite.controllers;

import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uk.ac.man.cs.eventlite.dao.EventService;
import uk.ac.man.cs.eventlite.dao.VenueService;
import uk.ac.man.cs.eventlite.entities.Event;
import uk.ac.man.cs.eventlite.entities.Venue;

public class HomeControllerTest {

    private MockMvc mockMvc;

    @Mock
    private EventService eventService;

    @Mock
    private VenueService venueService;

    @InjectMocks
    private HomeController homeController;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(homeController).build();
    }

    @Test
    public void testGetAllEventsReturnsHomepage() throws Exception {
        when(eventService.findTop3Events()).thenReturn(Collections.emptyList());
        when(venueService.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/home"))
            .andExpect(status().isOk())
            .andExpect(view().name("home/index"))
            .andExpect(model().attributeExists("events"))
            .andExpect(model().attributeExists("venues"));
    }

    @Test
    public void testGetAllEventsWithSampleData() throws Exception {
        Event e1 = new Event();
        e1.setName("Event 1");

        Event e2 = new Event();
        e2.setName("Event 2");

        Venue v1 = new Venue();
        v1.setName("Venue 1");

        when(eventService.findTop3Events()).thenReturn(Arrays.asList(e1, e2));
        when(venueService.findTop3Venues()).thenReturn(Collections.singletonList(v1));

        mockMvc.perform(get("/home"))
            .andExpect(status().isOk())
            .andExpect(view().name("home/index"))
            .andExpect(model().attribute("events", hasSize(2)))
            .andExpect(model().attribute("venues", hasSize(1)))
            .andExpect(model().attribute("events", hasItem(e1)))
            .andExpect(model().attribute("events", hasItem(e2)))
            .andExpect(model().attribute("venues", hasItem(v1)));
    }
}
