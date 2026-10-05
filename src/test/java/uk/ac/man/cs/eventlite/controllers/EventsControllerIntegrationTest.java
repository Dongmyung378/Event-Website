package uk.ac.man.cs.eventlite.controllers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit4.AbstractTransactionalJUnit4SpringContextTests;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import reactor.core.publisher.Mono;
import uk.ac.man.cs.eventlite.EventLite;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = EventLite.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"eventlite.admin.username=test-admin", "eventlite.admin.password=test-password"})
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
public class EventsControllerIntegrationTest extends AbstractTransactionalJUnit4SpringContextTests {

    @LocalServerPort
    private int port;

    private WebTestClient client;
    private String jsessionId;

    @BeforeEach
    public void setup() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        this.jsessionId = loginAndGetSessionId();
    }


    private String extractCsrfToken(String html) {
        Pattern pattern = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            throw new RuntimeException("Failed to get CSRF token");
        }
    }


    private String getCsrfToken(String uri) {
        EntityExchangeResult<String> result = client.get()
                .uri(uri)
                .header(HttpHeaders.COOKIE, jsessionId)
                .accept(MediaType.TEXT_HTML)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();
        return extractCsrfToken(result.getResponseBody());
    }


    private String loginAndGetSessionId() {
        EntityExchangeResult<String> loginPageResult = client.get()
                .uri("/sign-in")
                .accept(MediaType.TEXT_HTML)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();

        String csrfToken = extractCsrfToken(loginPageResult.getResponseBody());
        String cookie = loginPageResult.getResponseCookies().getFirst("JSESSIONID").getValue();

        EntityExchangeResult<Void> loginResult = client.post()
                .uri("/sign-in")
                .header(HttpHeaders.COOKIE, "JSESSIONID=" + cookie)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("username", "test-admin")
                                    .with("password", "test-password")
                                    .with("_csrf", csrfToken))
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().valueMatches(HttpHeaders.LOCATION, ".*/$")
                .expectBody(Void.class).returnResult();
        org.springframework.http.ResponseCookie session = loginResult.getResponseCookies().getFirst("JSESSIONID");
        org.junit.jupiter.api.Assertions.assertNotNull(session, "Successful login must issue an authenticated session cookie");
        return "JSESSIONID=" + session.getValue();
    }

    @Test
    public void testGetAllEvents() {
        client.get().uri("/events")
              .header(HttpHeaders.COOKIE, jsessionId)
              .accept(MediaType.TEXT_HTML)
              .exchange()
              .expectStatus().isOk();
    }

    @Test
    public void getEventNotFound() {
        client.get().uri("/events/99")
              .header(HttpHeaders.COOKIE, jsessionId)
              .accept(MediaType.TEXT_HTML)
              .exchange()
              .expectStatus().isNotFound()
              .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
              .expectBody(String.class)
              .consumeWith(result -> {
                  assertThat(result.getResponseBody(), containsString("99"));
              });
    }

    @Test
    public void testCreateEventSensibleData() {
        String csrfTokenVenue = getCsrfToken("/venues/new");
        client.post().uri("/venues")
              .header(HttpHeaders.COOKIE, jsessionId)
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(BodyInserters.fromFormData("name", "venue100")
                                  .with("capacity", "10")
                                  .with("road", "road1")
                                  .with("postcode", "M1")
                                  .with("_csrf", csrfTokenVenue))
              .exchange()
              .expectStatus().is3xxRedirection();

        Long venueId = jdbcTemplate.queryForObject("SELECT MAX(ID) FROM venue", Long.class);
        String firstVenueId = String.valueOf(venueId);

        String csrfTokenEvent = getCsrfToken("/events/new");
        client.post().uri("/events")
              .header(HttpHeaders.COOKIE, jsessionId)
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(BodyInserters.fromFormData("name", "Valid Event")
                                  .with("date", java.time.LocalDate.now().plusDays(1).toString())
                                  .with("time", "12:00")
                                  .with("venue.id", firstVenueId)
                                  .with("_csrf", csrfTokenEvent))
              .exchange()
              .expectStatus().is3xxRedirection()
              .expectHeader().valueMatches("Location", ".*/events$");
    }

    @Test
    public void testCreateEventMissingData() {
        String csrfTokenVenue = getCsrfToken("/venues/new");
        client.post().uri("/venues")
              .header(HttpHeaders.COOKIE, jsessionId)
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(BodyInserters.fromFormData("name", "venueMissingData")
                                  .with("capacity", "10")
                                  .with("road", "MissingRoad")
                                  .with("postcode", "M1")
                                  .with("_csrf", csrfTokenVenue))
              .exchange()
              .expectStatus().is3xxRedirection();

        String csrfTokenEvent = getCsrfToken("/events/new");
        client.post().uri("/events")
              .header(HttpHeaders.COOKIE, jsessionId)
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(BodyInserters.fromFormData("time", "12:00")
                                  .with("_csrf", csrfTokenEvent))
              .exchange()
              .expectStatus().isOk()
              .expectBody(String.class)
              .consumeWith(result -> {
                  String response = result.getResponseBody();
                  assertThat(response, containsString("action=\"/events\""));
              });
    }

    @Test
    public void testCreateEventInvalidData() {
        String csrfTokenVenue = getCsrfToken("/venues/new");
        client.post().uri("/venues")
              .header(HttpHeaders.COOKIE, jsessionId)
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(BodyInserters.fromFormData("name", "venueInvalidData")
                                  .with("capacity", "10")
                                  .with("road", "InvalidRoad")
                                  .with("postcode", "M1")
                                  .with("_csrf", csrfTokenVenue))
              .exchange()
              .expectStatus().is3xxRedirection();

        Long venueId = jdbcTemplate.queryForObject("SELECT MAX(ID) FROM venue", Long.class);

        String csrfTokenEvent = getCsrfToken("/events/new");
        client.post().uri("/events")
              .header(HttpHeaders.COOKIE, jsessionId)
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(BodyInserters.fromFormData("name", "")
                                  .with("date", "2020-01-01")
                                  .with("time", "12:00")
                                  .with("venue.id", String.valueOf(venueId))
                                  .with("_csrf", csrfTokenEvent))
              .exchange()
              .expectStatus().isOk()
              .expectBody(String.class)
              .consumeWith(result -> {
                  String response = result.getResponseBody();
                  assertThat(response, containsString("action=\"/events\""));
              });
    }

    @Test
    public void testUnauthorizedUserCannotCreateEvent() {
        String csrfTokenVenue = getCsrfToken("/venues/new");
        client.post().uri("/venues")
              .header(HttpHeaders.COOKIE, jsessionId)
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(BodyInserters.fromFormData("name", "venueUnauthorizedTest")
                                  .with("capacity", "10")
                                  .with("road", "TestRoad")
                                  .with("postcode", "M1")
                                  .with("_csrf", csrfTokenVenue))
              .exchange()
              .expectStatus().is3xxRedirection();

        Long venueId = jdbcTemplate.queryForObject("SELECT MAX(ID) FROM venue", Long.class);

        String csrfTokenEvent = getCsrfToken("/events/new");
        client.post().uri("/events")
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(BodyInserters.fromFormData("name", "Unauthorized Event")
                                  .with("date", java.time.LocalDate.now().plusDays(1).toString())
                                  .with("time", "12:00")
                                  .with("venue.id", String.valueOf(venueId))
                                  .with("_csrf", csrfTokenEvent))
              .exchange()
              .expectStatus().isForbidden();
    }
}
