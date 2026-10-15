package uk.ac.man.cs.eventlite.services;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import uk.ac.man.cs.eventlite.dao.EventRepository;
import uk.ac.man.cs.eventlite.dao.VenueRepository;
import uk.ac.man.cs.eventlite.entities.Event;
import uk.ac.man.cs.eventlite.entities.Venue;
import uk.ac.man.cs.eventlite.exceptions.EventNotFoundException;
import uk.ac.man.cs.eventlite.exceptions.MutationConflictException;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:mutation-tests;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "eventlite.admin.username=test-admin", "eventlite.admin.password=test-password",
    "eventlite.mastodon.instance=", "eventlite.mastodon.token=",
    "eventlite.mapbox.token=", "eventlite.mapbox.public-token="
})
@ActiveProfiles("mutation-test")
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMINISTRATOR")
class MutationServiceIntegrationTest {
    @Autowired MutationService mutations;
    @Autowired EventRepository events;
    @Autowired VenueRepository venues;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManager entityManager;
    @Autowired ObjectMapper mapper;
    @Autowired MockMvc mvc;
    @MockitoBean GeocodingService geocoding;
    private Venue venue;

    @BeforeEach
    void prepare() {
        events.deleteAll();
        venues.deleteAll();
        venue = new Venue();
        venue.setName("Mutation Venue");
        venue.setCapacity(100);
        venue.setAddress("Test Road", "M1 1AA");
        venue = mutations.createVenue(venue);
    }

    @Test
    void staleEventFormCannotOverwriteCommittedEdit() {
        Event saved = mutations.createEvent(eventForm("Original"));
        Event first = edit(saved, "First edit");
        Event stale = edit(saved, "Stale edit");
        mutations.updateEvent(saved.getId(), first);
        assertThrows(MutationConflictException.class, () -> mutations.updateEvent(saved.getId(), stale));
        assertEquals("First edit", events.findById(saved.getId()).orElseThrow().getName());
    }

    @Test
    void staleVenueFormCannotOverwriteCommittedEdit() {
        Venue first = venueForm("First edit", venue.getVersion());
        Venue stale = venueForm("Stale edit", venue.getVersion());
        mutations.updateVenue(venue.getId(), first);
        assertThrows(MutationConflictException.class, () -> mutations.updateVenue(venue.getId(), stale));
        assertEquals("First edit", venues.findById(venue.getId()).orElseThrow().getName());
    }

    @Test
    void missingVersionCannotBypassConflictDetection() {
        Event saved = mutations.createEvent(eventForm("Original"));
        Event submitted = edit(saved, "Without version");
        submitted.setVersion(null);
        assertThrows(MutationConflictException.class, () -> mutations.updateEvent(saved.getId(), submitted));
        assertThrows(MutationConflictException.class,
            () -> mutations.updateVenue(venue.getId(), venueForm("Without version", null)));
    }

    @Test
    void createResetsForgedIdAndVersion() {
        Event submitted = eventForm("New record");
        submitted.setId(999999);
        submitted.setVersion(52L);
        Event saved = mutations.createEvent(submitted);
        assertNotEquals(999999, saved.getId());
        assertEquals(0L, saved.getVersion());
        assertEquals(1, events.count());
    }

    @Test
    void missingUpdateDoesNotInsert() {
        assertThrows(EventNotFoundException.class,
            () -> mutations.updateEvent(999999, eventForm("Not an insertion")));
        assertEquals(0, events.count());
    }

    @Test
    void linkedVenueCannotBeDeletedAndCanBeRemovedAfterItsLastEvent() {
        Event saved = mutations.createEvent(eventForm("Linked"));
        assertFalse(mutations.deleteVenue(venue.getId()));
        assertTrue(venues.existsById(venue.getId()));
        mutations.deleteEvent(saved.getId());
        assertTrue(mutations.deleteVenue(venue.getId()));
    }

    @Test
    void deletedVenueCannotReceiveANewEvent() {
        assertTrue(mutations.deleteVenue(venue.getId()));
        assertThrows(MutationConflictException.class, () -> mutations.createEvent(eventForm("Orphan")));
        assertEquals(0, events.count());
    }

    @Test
    void versionIsInternalAndDoesNotChangeReadApiFields() throws Exception {
        Event saved = mutations.createEvent(eventForm("Public"));
        assertFalse(mapper.readTree(mapper.writeValueAsString(saved)).has("version"));
        assertFalse(mapper.readTree(mapper.writeValueAsString(venue)).has("version"));
    }

    @Test
    void twoEventFormSubmissionsShowAConflictAndPreserveTheFirstSave() throws Exception {
        Event saved = mutations.createEvent(eventForm("Original"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/events/update/" + saved.getId()))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                org.hamcrest.Matchers.containsString("name=\"version\" value=\"0\"")));
        for (int index = 0; index < 2; index++) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/events/updateSave/" + saved.getId())
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                    .param("version", "0").param("name", index == 0 ? "First web save" : "Stale web save")
                    .param("date", saved.getDate().toString()).param("venue.id", Long.toString(venue.getId())))
                .andExpect(index == 0
                    ? org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is3xxRedirection()
                    : org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
        }
        assertEquals("First web save", events.findById(saved.getId()).orElseThrow().getName());
    }

    @Test
    void twoVenueFormSubmissionsShowAConflictAndPreserveTheFirstSave() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/venues/update/" + venue.getId()))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                org.hamcrest.Matchers.containsString("name=\"version\" value=\"0\"")));
        for (int index = 0; index < 2; index++) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/venues/updateSave/" + venue.getId())
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                    .param("version", "0").param("name", index == 0 ? "First web venue" : "Stale web venue")
                    .param("capacity", "100").param("road", "Test Road").param("postcode", "M1 1AA"))
                .andExpect(index == 0
                    ? org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is3xxRedirection()
                    : org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
        }
        assertEquals("First web venue", venues.findById(venue.getId()).orElseThrow().getName());
    }

    @Test
    void changedAddressClearsOldCoordinatesWhenGeocodingIsUnavailable() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Venue existing = venues.findById(venue.getId()).orElseThrow();
            existing.setLatitude(53.48);
            existing.setLongitude(-2.24);
        });
        Venue existing = venues.findById(venue.getId()).orElseThrow();
        Venue submitted = venueForm("Moved venue", existing.getVersion());
        submitted.setAddress("New Road", "M2 2BB");
        mutations.updateVenue(venue.getId(), submitted);
        Venue saved = venues.findById(venue.getId()).orElseThrow();
        assertEquals("New Road", saved.getRoad());
        assertNull(saved.getLatitude());
        assertNull(saved.getLongitude());
    }

    @Test
    void simultaneousEventUpdatesCommitOnlyOneEdit() throws Exception {
        Event saved = mutations.createEvent(eventForm("Original"));
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> concurrentEdit(saved.getId(), "Editor A", barrier));
            Future<String> second = executor.submit(() -> concurrentEdit(saved.getId(), "Editor B", barrier));
            List<String> outcomes = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter("committed"::equals).count());
            assertEquals(1, outcomes.stream().filter("conflict"::equals).count());
            Event current = events.findById(saved.getId()).orElseThrow();
            assertTrue(List.of("Editor A", "Editor B").contains(current.getName()));
            assertEquals(saved.getVersion() + 1, current.getVersion());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void simultaneousVenueDeletionAndEventCreationKeepRelationshipValid() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> creation = executor.submit(() -> {
                await(barrier);
                try { mutations.createEvent(eventForm("Concurrent event")); return "created"; }
                catch (MutationConflictException | ConcurrencyFailureException exception) { return "conflict"; }
            });
            Future<Boolean> deletion = executor.submit(() -> {
                await(barrier);
                return mutations.deleteVenue(venue.getId());
            });
            String created = creation.get(15, TimeUnit.SECONDS);
            boolean deleted = deletion.get(15, TimeUnit.SECONDS);
            if (deleted) {
                assertEquals("conflict", created);
                assertEquals(0, events.count());
                assertFalse(venues.existsById(venue.getId()));
            } else {
                assertEquals("created", created);
                assertEquals(1, events.count());
                assertTrue(venues.existsById(venue.getId()));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private String concurrentEdit(long id, String name, CyclicBarrier barrier) {
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                Event original = events.findById(id).orElseThrow();
                Event form = edit(original, name);
                await(barrier);
                mutations.updateEvent(id, form);
            });
            return "committed";
        } catch (MutationConflictException | ConcurrencyFailureException | jakarta.persistence.OptimisticLockException exception) {
            return "conflict";
        }
    }

    private static void await(CyclicBarrier barrier) {
        try { barrier.await(10, TimeUnit.SECONDS); }
        catch (Exception exception) { throw new IllegalStateException("Concurrent test barrier failed", exception); }
    }

    private Event eventForm(String name) {
        Event result = new Event();
        result.setName(name);
        result.setDate(LocalDate.now().plusDays(2));
        result.setVenue(venue);
        return result;
    }

    private Event edit(Event saved, String name) {
        Event result = eventForm(name);
        result.setVersion(saved.getVersion());
        return result;
    }

    private Venue venueForm(String name, Long version) {
        Venue result = new Venue();
        result.setName(name);
        result.setCapacity(100);
        result.setAddress("Test Road", "M1 1AA");
        result.setVersion(version);
        return result;
    }
}
