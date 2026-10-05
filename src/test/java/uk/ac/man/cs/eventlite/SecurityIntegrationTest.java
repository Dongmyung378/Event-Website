package uk.ac.man.cs.eventlite;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static uk.ac.man.cs.eventlite.testutil.FormUtil.getCsrfToken;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import uk.ac.man.cs.eventlite.dao.EventService;
import uk.ac.man.cs.eventlite.dao.VenueService;
import uk.ac.man.cs.eventlite.entities.Event;
import uk.ac.man.cs.eventlite.entities.Venue;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = EventLite.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext
public class SecurityIntegrationTest {

    private final static String SESSION_KEY = "JSESSIONID";
    private final static String LOGIN_PATH = "/sign-in";
    private final static String LOGIN_ERROR = LOGIN_PATH + "?error";
    private final static String LOGOUT_PATH = "/sign-out";

    @LocalServerPort
    private int port;

    private String baseUri;

    private WebTestClient client;

    @Autowired
    private EventService eventService;

    @Autowired
    private VenueService venueService;

    private Venue venueA;
    private Venue venueB;
    private Event eventAlpha, eventBeta, eventApple, eventFormer, eventPrevious, eventPast;

    @BeforeEach
    public void setUp() {
        baseUri = "http://localhost:" + port;
        client = WebTestClient.bindToServer().baseUrl(baseUri).build();

        // Create a test site
        venueA = new Venue();
        venueA.setName("Venue A");
        venueA.setCapacity(1000);
        venueA.setAddress("Test Road A", "M1 1AA");
        venueA = venueService.save(venueA);

        venueB = new Venue();
        venueB.setName("Venue B");
        venueB.setCapacity(500);
        venueB.setAddress("Test Road B", "M1 2AA");
        venueB = venueService.save(venueB);

        // Create events (with future and past events for subsequent testing)
        eventAlpha = new Event();
        eventAlpha.setName("Event Alpha");
        eventAlpha.setDate(LocalDate.now().plusDays(10));
        eventAlpha.setTime(LocalTime.of(12, 30));
        eventAlpha.setVenue(venueB);
        eventAlpha.setDescription("Event Alpha is the first of its kind...");
        eventAlpha = eventService.save(eventAlpha);

        eventBeta = new Event();
        eventBeta.setName("Event Beta");
        eventBeta.setDate(LocalDate.now().plusDays(10));
        eventBeta.setTime(LocalTime.of(10, 0));
        eventBeta.setVenue(venueA);
        eventBeta.setDescription("Details for Event Beta...");
        eventBeta = eventService.save(eventBeta);

        eventApple = new Event();
        eventApple.setName("Event Apple");
        eventApple.setDate(LocalDate.now().plusDays(11));
        eventApple.setTime(null);
        eventApple.setVenue(venueA);
        eventApple.setDescription("Event Apple will be host to some of the world’s best iOS developers...");
        eventApple = eventService.save(eventApple);

        eventFormer = new Event();
        eventFormer.setName("Event Former");
        eventFormer.setDate(LocalDate.now().minusDays(10));
        eventFormer.setTime(LocalTime.of(11, 0));
        eventFormer.setVenue(venueB);
        eventFormer.setDescription("Details for Event Former...");
        eventFormer = eventService.save(eventFormer);

        eventPrevious = new Event();
        eventPrevious.setName("Event Previous");
        eventPrevious.setDate(LocalDate.now().minusDays(10));
        eventPrevious.setTime(LocalTime.of(18, 30));
        eventPrevious.setVenue(venueA);
        eventPrevious.setDescription("Details for Event Previous...");
        eventPrevious = eventService.save(eventPrevious);

        eventPast = new Event();
        eventPast.setName("Event Past");
        eventPast.setDate(LocalDate.now().minusDays(11));
        eventPast.setTime(LocalTime.of(17, 0));
        eventPast.setVenue(venueA);
        eventPast.setDescription("Details for Event Past...");
        eventPast = eventService.save(eventPast);
    }

    @Test
    public void signIn() throws Exception {
        // GET the login form, read the CSRF token and session cookie.
        EntityExchangeResult<String> result = client.get().uri(LOGIN_PATH)
                .accept(MediaType.TEXT_HTML)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();
        String csrfToken = getCsrfToken(result.getResponseBody());
        String cookie = result.getResponseCookies().getFirst(SESSION_KEY).getValue();

        // Prepare the login form data
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("_csrf", csrfToken);
        form.add("username", "test-admin");
        form.add("password", "test-password");

        client.post().uri(LOGIN_PATH)
            .accept(MediaType.TEXT_HTML)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .cookie(SESSION_KEY, cookie)
            .bodyValue(form)
            .exchange()
            .expectStatus().isFound()
            .expectHeader().value("Location", endsWith("/"));
    }

    @Test
    public void signInBadUser() throws Exception {
        EntityExchangeResult<String> result = client.get().uri(LOGIN_PATH)
                .accept(MediaType.TEXT_HTML)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();
        String csrfToken = getCsrfToken(result.getResponseBody());
        String cookie = result.getResponseCookies().getFirst(SESSION_KEY).getValue();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("_csrf", csrfToken);
        form.add("username", "Robert");
        form.add("password", "test-password");

        client.post().uri(LOGIN_PATH)
            .accept(MediaType.TEXT_HTML)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .cookie(SESSION_KEY, cookie)
            .bodyValue(form)
            .exchange()
            .expectStatus().isFound()
            .expectHeader().value("Location", endsWith(LOGIN_ERROR));
    }

    @Test
    public void signOut() throws Exception {
        EntityExchangeResult<String> result = client.get().uri(LOGIN_PATH)
                .accept(MediaType.TEXT_HTML)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();
        String csrfToken = getCsrfToken(result.getResponseBody());
        String cookie = result.getResponseCookies().getFirst(SESSION_KEY).getValue();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("_csrf", csrfToken);
        form.add("username", "test-admin");
        form.add("password", "test-password");

        result = client.post().uri(LOGIN_PATH)
                .accept(MediaType.TEXT_HTML)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .cookie(SESSION_KEY, cookie)
                .bodyValue(form)
                .exchange()
                .expectStatus().isFound()
                .expectHeader().value("Location", endsWith("/"))
                .expectBody(String.class)
                .returnResult();
        cookie = result.getResponseCookies().getFirst(SESSION_KEY).getValue();

        result = client.get().uri("/events")
                .accept(MediaType.TEXT_HTML)
                .cookie(SESSION_KEY, cookie)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();
        csrfToken = getCsrfToken(result.getResponseBody());

        MultiValueMap<String, String> logoutForm = new LinkedMultiValueMap<>();
        logoutForm.add("_csrf", csrfToken);

        client.post().uri(LOGOUT_PATH)
            .accept(MediaType.TEXT_HTML)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .cookie(SESSION_KEY, cookie)
            .bodyValue(logoutForm)
            .exchange()
            .expectStatus().isFound()
            .expectHeader().value("Location", endsWith("/"));
    }

    // Administrator action testing: adding, modifying, deleting events
    @Test
    public void testAddEvent() throws Exception {
        // Simulate an administrator login to get a cookie
        EntityExchangeResult<String> result = client.get().uri(LOGIN_PATH)
                .accept(MediaType.TEXT_HTML)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();
        String csrfToken = getCsrfToken(result.getResponseBody());
        String cookie = result.getResponseCookies().getFirst(SESSION_KEY).getValue();
        MultiValueMap<String, String> loginForm = new LinkedMultiValueMap<>();
        loginForm.add("_csrf", csrfToken);
        loginForm.add("username", "test-admin");
        loginForm.add("password", "test-password");

        result = client.post().uri(LOGIN_PATH)
                .accept(MediaType.TEXT_HTML)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .cookie(SESSION_KEY, cookie)
                .bodyValue(loginForm)
                .exchange()
                .expectStatus().isFound()
                .expectHeader().value("Location", endsWith("/"))
                .expectBody(String.class)
                .returnResult();
        cookie = result.getResponseCookies().getFirst(SESSION_KEY).getValue();

        // Visit /events to get a CSRF token.
        result = client.get().uri("/events")
                .accept(MediaType.TEXT_HTML)
                .cookie(SESSION_KEY, cookie)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();
        csrfToken = getCsrfToken(result.getResponseBody());

        // Initiate a request to add an event
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        String newName = "New Event";
        String newDate = LocalDate.now().plusDays(10).toString();
        String newTime = "15:00";
        String newDescription = "Description for new event.";
        form.add("name", newName);
        form.add("date", newDate);
        form.add("time", newTime);
        form.add("venue.id", String.valueOf(venueA.getId()));
        form.add("description", newDescription);
        form.add("_csrf", csrfToken);

        client.post().uri("/events")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .cookie(SESSION_KEY, cookie)
            .bodyValue(form)
            .exchange()
            .expectStatus().isFound()
            .expectHeader().value("Location", endsWith("/events"));

        List<Event> events = eventService.findByNameContainingIgnoreCase(newName);
        assertFalse(events.isEmpty(), "The new event should be saved in the database");
    }

    @Test
    public void testUpdateEvent() throws Exception {
        // Simulate administrator login
        EntityExchangeResult<String> result = client.get().uri(LOGIN_PATH)
                .accept(MediaType.TEXT_HTML)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();
        String csrfToken = getCsrfToken(result.getResponseBody());
        String cookie = result.getResponseCookies().getFirst(SESSION_KEY).getValue();
        MultiValueMap<String, String> loginForm = new LinkedMultiValueMap<>();
        loginForm.add("_csrf", csrfToken);
        loginForm.add("username", "test-admin");
        loginForm.add("password", "test-password");

        result = client.post().uri(LOGIN_PATH)
                .accept(MediaType.TEXT_HTML)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .cookie(SESSION_KEY, cookie)
                .bodyValue(loginForm)
                .exchange()
                .expectStatus().isFound()
                .expectHeader().value("Location", endsWith("/"))
                .expectBody(String.class)
                .returnResult();
        cookie = result.getResponseCookies().getFirst(SESSION_KEY).getValue();

        // Get Event Beta to modify (lookup directly from service)
        List<Event> betaList = eventService.findByNameContainingIgnoreCase("Event Beta");
        assertFalse(betaList.isEmpty(), "Event Beta should exist");
        Event eventBetaToUpdate = betaList.get(0);

        // Visit /events to get a CSRF token.
        result = client.get().uri("/events")
                .accept(MediaType.TEXT_HTML)
                .cookie(SESSION_KEY, cookie)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();
        csrfToken = getCsrfToken(result.getResponseBody());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        String updatedName = "Updated Event Beta";
        String updatedDate = eventBetaToUpdate.getDate().toString();
        String updatedTime = (eventBetaToUpdate.getTime() != null ? eventBetaToUpdate.getTime().toString() : "");
        String updatedDescription = "Updated description for Event Beta.";
        form.add("name", updatedName);
        form.add("date", updatedDate);
        form.add("time", updatedTime);
        form.add("venue.id", String.valueOf(eventBetaToUpdate.getVenue().getId()));
        form.add("description", updatedDescription);
        form.add("_csrf", csrfToken);

        client.post().uri("/events/updateSave/" + eventBetaToUpdate.getId())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .cookie(SESSION_KEY, cookie)
            .bodyValue(form)
            .exchange()
            .expectStatus().isFound()
            .expectHeader().value("Location", endsWith("/events"));

        Optional<Event> updated = eventService.findById(eventBetaToUpdate.getId());
        assertTrue(updated.isPresent(), "Updated event should be retrievable");
        assertEquals(updatedName, updated.get().getName(), "Event name should be updated");
        assertEquals(updatedDescription, updated.get().getDescription(), "Event description should be updated");
    }

    @Test
    public void testDeleteEvent() throws Exception {
        // Simulate administrator login
        EntityExchangeResult<String> result = client.get().uri(LOGIN_PATH)
                .accept(MediaType.TEXT_HTML)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();
        String csrfToken = getCsrfToken(result.getResponseBody());
        String cookie = result.getResponseCookies().getFirst(SESSION_KEY).getValue();
        MultiValueMap<String, String> loginForm = new LinkedMultiValueMap<>();
        loginForm.add("_csrf", csrfToken);
        loginForm.add("username", "test-admin");
        loginForm.add("password", "test-password");

        result = client.post().uri(LOGIN_PATH)
                .accept(MediaType.TEXT_HTML)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .cookie(SESSION_KEY, cookie)
                .bodyValue(loginForm)
                .exchange()
                .expectStatus().isFound()
                .expectHeader().value("Location", endsWith("/"))
                .expectBody(String.class)
                .returnResult();
        cookie = result.getResponseCookies().getFirst(SESSION_KEY).getValue();

        // Find Event Former
        List<Event> formerList = eventService.findByNameContainingIgnoreCase("Event Former");
        assertFalse(formerList.isEmpty(), "Event Former should exist");
        Event eventFormerToDelete = formerList.get(0);

        // Visit /events to get a CSRF token.
        result = client.get().uri("/events")
                .accept(MediaType.TEXT_HTML)
                .cookie(SESSION_KEY, cookie)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();
        csrfToken = getCsrfToken(result.getResponseBody());

        client.delete().uri("/events/" + eventFormerToDelete.getId())
            .cookie(SESSION_KEY, cookie)
            .header("X-CSRF-TOKEN", csrfToken)
            .exchange()
            .expectStatus().isFound()
            .expectHeader().value("Location", endsWith("/events"));

        Optional<Event> deleted = eventService.findById(eventFormerToDelete.getId());
        assertFalse(deleted.isPresent(), "Event Former should be deleted from the database");
    }
}
