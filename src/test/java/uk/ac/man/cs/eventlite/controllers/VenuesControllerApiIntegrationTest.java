package uk.ac.man.cs.eventlite.controllers;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit4.AbstractTransactionalJUnit4SpringContextTests;
import org.springframework.test.web.reactive.server.WebTestClient;

import uk.ac.man.cs.eventlite.EventLite;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = EventLite.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
public class VenuesControllerApiIntegrationTest extends AbstractTransactionalJUnit4SpringContextTests {

    @LocalServerPort
    private int port;

    private WebTestClient client;

    @BeforeEach
    public void setup() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port + "/api").build();
    }

    @Test
    public void testGetAllVenues() {
        client.get().uri("/venues")
              .accept(MediaType.APPLICATION_JSON)
              .exchange()
              .expectStatus().isOk()
              .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
              .expectBody()
              .jsonPath("$._links.self.href").exists()
              .jsonPath("$._links.profile").doesNotExist();
    }


    @Test
    public void getVenueNotFound() {
        client.get().uri("/venues/99")
              .accept(MediaType.APPLICATION_JSON)
              .exchange()
              .expectStatus().isNotFound()
              .expectHeader().contentType(MediaType.APPLICATION_JSON)
              .expectBody()
              .jsonPath("$.error").value(containsString("venue 99"))
              .jsonPath("$.id").isEqualTo(99);
    }

    @Test
    public void testGetVenue() {
        long validId = 1L;

        client.get().uri("/venues/" + validId)
              .accept(MediaType.APPLICATION_JSON)
              .exchange()
              .expectStatus().isOk()
              .expectBody()
              .jsonPath("$._links.self.href").exists()
              .jsonPath("$._links.venue.href").exists()
              .jsonPath("$._links.events.href").exists()
              .jsonPath("$._links.next3events.href").exists();
    }

    @Test
    public void testGetVenueEvents() {
        long validId = 1L;

        client.get().uri("/venues/" + validId + "/events")
              .accept(MediaType.APPLICATION_JSON)
              .exchange()
              .expectStatus().isOk()
              .expectBody()
              .jsonPath("$._links.self.href").exists();
    }

    @Test
    public void testGetVenueNext3Events() {
        long validId = 1L;

        client.get().uri("/venues/" + validId + "/next3events")
              .accept(MediaType.APPLICATION_JSON)
              .exchange()
              .expectStatus().isOk()
              .expectBody()
              .jsonPath("$._links.self.href").exists();
    }

    @Test
    public void testGetVenueEventsNotFound() {
        long invalidId = 99L;

        client.get().uri("/venues/" + invalidId + "/events")
              .accept(MediaType.APPLICATION_JSON)
              .exchange()
              .expectStatus().isNotFound()
              .expectBody();
    }

    @Test
    public void testGetVenueNext3EventsNotFound() {
        long invalidId = 99L;

        client.get().uri("/venues/" + invalidId + "/next3events")
              .accept(MediaType.APPLICATION_JSON)
              .exchange()
              .expectStatus().isNotFound()
              .expectBody();
    }
}
