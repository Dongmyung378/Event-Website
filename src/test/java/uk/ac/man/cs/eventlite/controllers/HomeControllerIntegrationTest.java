package uk.ac.man.cs.eventlite.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import uk.ac.man.cs.eventlite.dao.EventService;
import uk.ac.man.cs.eventlite.dao.VenueService;

import java.util.Collections;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.security.test.context.support.WithMockUser;

@WebMvcTest(HomeController.class)
public class HomeControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EventService eventService;

    @MockBean
    private VenueService venueService;

    @Test
    @WithMockUser(roles = "USER")
    public void testHomePageLoads() throws Exception {
        when(eventService.findTop3Events()).thenReturn(Collections.emptyList());
        when(venueService.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/home"))
            .andDo(print()) // Shows detailed request/response info in console
            .andExpect(status().isOk())
            .andExpect(view().name("home/index"))
            .andExpect(model().attributeExists("events"))
            .andExpect(model().attributeExists("venues"));
    }
}
