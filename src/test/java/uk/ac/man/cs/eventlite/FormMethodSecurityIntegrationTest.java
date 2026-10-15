package uk.ac.man.cs.eventlite;

import static org.junit.jupiter.api.Assertions.*;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import uk.ac.man.cs.eventlite.dao.EventService;
import uk.ac.man.cs.eventlite.dao.VenueService;
import uk.ac.man.cs.eventlite.entities.Event;
import uk.ac.man.cs.eventlite.entities.Venue;

/** Real servlet-container coverage for Thymeleaf's POST + _method=delete flow. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:form-method-security;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FormMethodSecurityIntegrationTest {
    @LocalServerPort int port;
    @Autowired EventService events;
    @Autowired VenueService venues;
    @Autowired UserDetailsService users;
    @Autowired PasswordEncoder encoder;
    private Venue venue;

    @BeforeEach
    void prepare() {
        venue = new Venue();
        venue.setName("Form method venue");
        venue.setCapacity(100);
        venue.setAddress("Test Road", "M1 1AA");
        venue = venues.save(venue);
        InMemoryUserDetailsManager manager = (InMemoryUserDetailsManager) users;
        if (!manager.userExists("form-reader")) {
            manager.createUser(User.withUsername("form-reader").password(encoder.encode("reader-password"))
                .roles("USER").build());
        }
    }

    @Test
    void administratorCanDeleteVenueThroughItsRenderedPostForm() throws Exception {
        HttpClient client = client();
        String page = read(client, "/venues/" + venue.getId(), "test-admin:test-password");
        assertTrue(page.contains("name=\"_method\" value=\"delete\""));
        HttpResponse<String> result = submit(client, "/venues/" + venue.getId(), "delete", csrf(page), "test-admin:test-password");
        assertEquals(302, result.statusCode());
        assertTrue(result.headers().firstValue("Location").orElseThrow().endsWith("/venues"));
        assertFalse(venues.existsById(venue.getId()));
    }

    @Test
    void administratorCanDeleteEventThroughItsRenderedPostForm() throws Exception {
        Event event = event();
        HttpClient client = client();
        String page = read(client, "/events/" + event.getId(), "test-admin:test-password");
        assertTrue(page.contains("name=\"_method\" value=\"delete\""));
        assertEquals(302, submit(client, "/events/" + event.getId(), "delete", csrf(page), "test-admin:test-password").statusCode());
        assertFalse(events.existsById(event.getId()));
    }

    @Test
    void referencedVenueFormExplainsWhyDeletionWasRefused() throws Exception {
        event();
        HttpClient client = client();
        String path = "/venues/" + venue.getId();
        String page = read(client, path, "test-admin:test-password");
        HttpResponse<String> result = submit(client, path, "delete", csrf(page), "test-admin:test-password");
        assertEquals(302, result.statusCode());
        assertTrue(result.headers().firstValue("Location").orElseThrow().endsWith(path));
        assertTrue(venues.existsById(venue.getId()));
        assertTrue(read(client, path, "test-admin:test-password").contains("Venue cannot be deleted because it has events."));
    }

    @Test
    void anonymousOverrideStillRequiresLoginWithValidCsrf() throws Exception {
        HttpClient client = client();
        String token = csrf(read(client, "/sign-in", null));
        HttpResponse<String> result = submit(client, "/venues/" + venue.getId(), "delete", token, null);
        assertEquals(302, result.statusCode());
        assertTrue(result.headers().firstValue("Location").orElseThrow().endsWith("/sign-in"));
        assertTrue(venues.existsById(venue.getId()));
    }

    @Test
    void ordinaryUserOverrideRemainsForbiddenWithValidCsrf() throws Exception {
        HttpClient client = client();
        String token = csrf(read(client, "/sign-in", "form-reader:reader-password"));
        assertEquals(403, submit(client, "/venues/" + venue.getId(), "delete", token, "form-reader:reader-password").statusCode());
        assertTrue(venues.existsById(venue.getId()));
    }

    @Test
    void administratorOverrideWithoutCsrfRemainsForbidden() throws Exception {
        HttpClient client = client();
        assertEquals(403, submit(client, "/venues/" + venue.getId(), "delete", null, "test-admin:test-password").statusCode());
        assertTrue(venues.existsById(venue.getId()));
    }

    @Test
    void methodOverrideDoesNotEnableApiWritesOrUnsupportedMethods() throws Exception {
        HttpClient client = client();
        String token = csrf(read(client, "/sign-in", "test-admin:test-password"));
        assertEquals(403, submit(client, "/api/venues/" + venue.getId(), "delete", token, "test-admin:test-password").statusCode());
        assertEquals(403, submit(client, "/venues/" + venue.getId(), "put", token, "test-admin:test-password").statusCode());
        assertEquals(403, submit(client, "/venues/" + venue.getId(), "patch", token, "test-admin:test-password").statusCode());
        assertTrue(venues.existsById(venue.getId()));
    }

    private Event event() {
        Event event = new Event();
        event.setName("Form method event");
        event.setDate(LocalDate.now().plusDays(3));
        event.setVenue(venue);
        return events.save(event);
    }

    private static HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    private String read(HttpClient client, String path, String credentials) throws Exception {
        HttpResponse<String> response = client.send(request(path, credentials).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), path);
        return response.body();
    }

    private HttpResponse<String> submit(HttpClient client, String path, String method, String token, String credentials) throws Exception {
        String body = "_method=" + method + (token == null ? "" : "&_csrf=" + URLEncoder.encode(token, StandardCharsets.UTF_8));
        return client.send(request(path, credentials).header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder request(String path, String credentials) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .header("Accept", "text/html");
        if (credentials != null) request.header("Authorization", "Basic "
            + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        return request;
    }

    private static String csrf(String html) {
        var matcher = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"").matcher(html);
        assertTrue(matcher.find(), "Rendered page must include a CSRF token");
        return matcher.group(1);
    }
}
