package uk.ac.man.cs.eventlite;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.hateoas.CollectionModel;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import uk.ac.man.cs.eventlite.assemblers.EventModelAssembler;
import uk.ac.man.cs.eventlite.assemblers.VenueModelAssembler;
import uk.ac.man.cs.eventlite.config.Security;
import uk.ac.man.cs.eventlite.controllers.*;
import uk.ac.man.cs.eventlite.dao.*;
import uk.ac.man.cs.eventlite.entities.*;
import uk.ac.man.cs.eventlite.services.MutationService;

@WebMvcTest(controllers = {EventsController.class, VenuesController.class,
        EventsControllerApi.class, VenuesControllerApi.class})
@Import(Security.class)
class MutationSecurityTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private EventService eventService;
    @MockitoBean private VenueService venueService;
    @MockitoBean private MastodonService mastodonService;
    @MockitoBean private EventModelAssembler eventAssembler;
    @MockitoBean private VenueModelAssembler venueAssembler;
    @MockitoBean private MutationService mutationService;

    static Stream<MockHttpServletRequestBuilder> mutations() {
        return Stream.of(delete("/events/1"), delete("/venues/1"), post("/events/1").param("m", "Test post"),
                post("/events"), post("/venues"), post("/events/updateSave/1"), post("/venues/updateSave/1"),
                get("/events/new"), get("/venues/new"), get("/events/update/1"), get("/venues/update/1"));
    }

    @ParameterizedTest
    @MethodSource("mutations")
    void anonymousMutationsRequireLoginEvenWithValidCsrf(MockHttpServletRequestBuilder request) throws Exception {
        mvc.perform(request.accept(MediaType.TEXT_HTML).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("http://localhost/sign-in"));
        verifyNoInteractions(eventService, venueService, mutationService);
        verify(mastodonService, never()).postStatus(anyString());
    }

    @ParameterizedTest
    @MethodSource("mutations")
    void ordinaryUsersCannotMutateEvenWithValidCsrf(MockHttpServletRequestBuilder request) throws Exception {
        mvc.perform(request.with(user("reader").roles("USER")).with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(eventService, venueService, mutationService);
        verify(mastodonService, never()).postStatus(anyString());
    }

    @Test
    void administratorsCanDeleteThroughActualControllerRoutes() throws Exception {
        when(mutationService.deleteVenue(1L)).thenReturn(true);
        mvc.perform(delete("/events/1").with(user("admin").roles(Security.ADMIN_ROLE)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(delete("/venues/1").with(user("admin").roles(Security.ADMIN_ROLE)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        verify(mutationService).deleteEvent(1L);
        verify(mutationService).deleteVenue(1L);
    }

    @Test
    void administratorsStillNeedCsrfForMutation() throws Exception {
        mvc.perform(delete("/events/1").with(user("admin").roles(Security.ADMIN_ROLE)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/events/1").param("m", "Test post").with(user("admin").roles(Security.ADMIN_ROLE)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(eventService, venueService, mutationService);
        verify(mastodonService, never()).postStatus(anyString());
    }

    @Test
    void manualApiReadsRemainPublicButAllApiWritesAreDenied() throws Exception {
        when(eventService.findAll()).thenReturn(List.of());
        when(eventAssembler.toCollectionModel(any())).thenReturn(CollectionModel.empty());
        mvc.perform(get("/api/events").accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
        for (HttpMethod method : List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)) {
            mvc.perform(request(method, "/api/events/1").with(user("admin").roles(Security.ADMIN_ROLE)).with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void consoleAndUnknownWriteRoutesAreDenied() throws Exception {
        mvc.perform(get("/h2-console/").with(user("admin").roles(Security.ADMIN_ROLE)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/unlisted-write").with(user("admin").roles(Security.ADMIN_ROLE)).with(csrf()))
                .andExpect(status().isForbidden());
    }
}
