package uk.ac.man.cs.eventlite.controllers;

import static org.hamcrest.Matchers.containsString;

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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;

import uk.ac.man.cs.eventlite.EventLite;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = EventLite.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"eventlite.admin.username=test-admin", "eventlite.admin.password=test-password"})
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
public class VenuesControllerIntegrationTest extends AbstractTransactionalJUnit4SpringContextTests {

    @LocalServerPort
    private int port;

    private WebTestClient client;
    private String jsessionId;

    @BeforeEach
    public void setup() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        jsessionId = loginAndGetSessionId();
    }

    private String extractVersion(String html) {
        Matcher matcher = Pattern.compile("name=\"version\"[^>]*value=\"([0-9]+)\"").matcher(html);
        if (!matcher.find()) throw new IllegalStateException("Missing edit version");
        return matcher.group(1);
    }

    private String extractCsrfToken(String html) {
        Pattern pattern = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            throw new RuntimeException("Failed to extract CSRF token");
        }
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
    public void testGetAllVenues() {
        client.get().uri("/venues")
              .accept(MediaType.TEXT_HTML)
              .header(HttpHeaders.COOKIE, jsessionId)
              .exchange()
              .expectStatus().isOk()
              .expectBody(String.class)
              .value(containsString("Venue A"))
              .value(containsString("Venue B"))
              .value(containsString("Venue C"))
              .value(containsString("Venue D"));
    }

    @Test
    public void testGetVenueById() {
        client.get().uri("/venues/1")
              .accept(MediaType.TEXT_HTML)
              .header(HttpHeaders.COOKIE, jsessionId)
              .exchange()
              .expectStatus().isOk()
              .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
              .expectBody(String.class)
              .value(containsString("1000"))
              .value(containsString("Etihad"))
              .value(containsString("M1"))
              .value(containsString("Venue A"));
    }

    @Test
    public void testSearchVenues() {
        client.get()
              .uri(uriBuilder -> uriBuilder
                  .path("/venues/search")
                  .queryParam("q", "Venue")
                  .build())
              .accept(MediaType.TEXT_HTML)
              .header(HttpHeaders.COOKIE, jsessionId)
              .exchange()
              .expectStatus().isOk()
              .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
              .expectBody(String.class)
              .value(containsString("Venue A"));
    }

    @Test
    public void testUpdateVenueForm() {
        client.get().uri("/venues/update/1")
              .accept(MediaType.TEXT_HTML)
              .header(HttpHeaders.COOKIE, jsessionId)
              .exchange()
              .expectStatus().isOk()
              .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
              .expectBody(String.class)
              .value(containsString("Venue A"))
              .value(containsString("Etihad"))
              .value(containsString("1000"))
              .value(containsString("M1"));
    }

    @Test
    public void getVenueNotFound() {
        client.get().uri("/venues/99")
              .accept(MediaType.TEXT_HTML)
              .header(HttpHeaders.COOKIE, jsessionId)
              .exchange()
              .expectStatus().isNotFound()
              .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
              .expectBody(String.class)
              .consumeWith(result -> {
                  org.hamcrest.MatcherAssert.assertThat(result.getResponseBody(), containsString("99"));
              });
    }

    @Test
    public void testPostNewVenue() {
        String newVenuePage = client.get()
                .uri("/venues/new")
                .header(HttpHeaders.COOKIE, jsessionId)
                .accept(MediaType.TEXT_HTML)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        String newCsrfToken = extractCsrfToken(newVenuePage);


        MultiValueMap<String, String> venueForm = new LinkedMultiValueMap<>();
        venueForm.add("name", "New Venue");
        venueForm.add("capacity", "500");
        venueForm.add("road", "Test Road");
        venueForm.add("postcode", "M2 3AB");
        venueForm.add("_csrf", newCsrfToken);


        client.post().uri("/venues")
              .header(HttpHeaders.COOKIE, jsessionId)
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(BodyInserters.fromFormData(venueForm))
              .exchange()
              .expectStatus().is3xxRedirection()
              .expectHeader().valueMatches("Location", ".*/venues.*");


        client.get().uri("/venues")
              .header(HttpHeaders.COOKIE, jsessionId)
              .accept(MediaType.TEXT_HTML)
              .exchange()
              .expectStatus().isOk()
              .expectBody(String.class)
              .value(containsString("New Venue"));
    }

    @Test
    public void testUpdateSaveWithInvalidId() {
         String newVenuePage = client.get()
                .uri("/venues/new")
                .header(HttpHeaders.COOKIE, jsessionId)
                .accept(MediaType.TEXT_HTML)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        String newCsrfToken = extractCsrfToken(newVenuePage);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("name", "New Venue");
        formData.add("capacity", "500");
        formData.add("road", "Test Road");
        formData.add("postcode", "M2 3AB");
        formData.add("_csrf", newCsrfToken);

        client.post().uri("/venues/updateSave/999")
            .header(HttpHeaders.COOKIE, jsessionId)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters.fromFormData(formData))
            .exchange()
            .expectStatus().isNotFound()
            .expectBody(String.class)
            .value(containsString("999"));
    }

    @Test
    public void testUpdateSaveWithvalidId() {
         String newVenuePage = client.get()
                .uri("/venues/update/1")
                .header(HttpHeaders.COOKIE, jsessionId)
                .accept(MediaType.TEXT_HTML)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        String newCsrfToken = extractCsrfToken(newVenuePage);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("version", extractVersion(newVenuePage));
        formData.add("name", "New Venue");
        formData.add("capacity", "500");
        formData.add("road", "Test Road");
        formData.add("postcode", "M2 3AB");
        formData.add("_csrf", newCsrfToken);

        client.post().uri("/venues/updateSave/1")
            .header(HttpHeaders.COOKIE, jsessionId)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters.fromFormData(formData))
            .exchange()
            .expectStatus().is3xxRedirection()
            .expectHeader().valueMatches("Location", ".*/venues.*")
              .expectBody().isEmpty();
    }

    @Test
    public void testGetVenueEventsWithInvalidId() {
        long invalidId = 99L;

        client.get()
              .uri("/venues/" + invalidId + "/events")
              .header(HttpHeaders.COOKIE, jsessionId)
              .accept(MediaType.TEXT_HTML)
              .exchange()
              .expectStatus().isNotFound();
    }

    @Test
    public void testGetVenueNext3EventsWithInvalidId() {
        long invalidId = 99L;

        client.get()
              .uri("/venues/" + invalidId + "/next3events")
              .header(HttpHeaders.COOKIE, jsessionId)
              .accept(MediaType.TEXT_HTML)
              .exchange()
              .expectStatus().isNotFound();
    }

    @Test
    public void testUpdateVenueSaveSuccess() {
        String newVenuePage = client.get()
                .uri("/venues/update/1")
                .header(HttpHeaders.COOKIE, jsessionId)
                .accept(MediaType.TEXT_HTML)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        String newCsrfToken = extractCsrfToken(newVenuePage);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("version", extractVersion(newVenuePage));
             formData.add("name", "Updated Venue");
             formData.add("capacity", "150");
	        formData.add("road", "Updated Road");
	        formData.add("postcode", "UPD1 1A");
	        formData.add("_csrf", newCsrfToken);

        client.post().uri("/venues/updateSave/1")
              .header(HttpHeaders.COOKIE, jsessionId)
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(BodyInserters.fromFormData(formData))
              .exchange()
              .expectStatus().is3xxRedirection()
                .expectHeader().valueMatches("Location", ".*/venues.*");

        client.get().uri("/venues/1")
		        .header(HttpHeaders.COOKIE, jsessionId)
		        .accept(MediaType.TEXT_HTML)
		        .exchange()
		        .expectStatus().isOk()
		        .expectBody(String.class)
		        .value(containsString("Updated Venue"))
		        .value(containsString("Updated Road"))
		        .value(containsString("UPD1 1A"));
    }

    @Test
    public void testUpdateVenueSaveInvalidCapacity() {
        String newVenuePage = client.get()
                .uri("/venues/update/1")
                .header(HttpHeaders.COOKIE, jsessionId)
                .accept(MediaType.TEXT_HTML)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        String csrfToken = extractCsrfToken(newVenuePage);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
		        formData.add("name", "Updated Venue");
		        formData.add("capacity", "-10");
		        formData.add("road", "Updated Road");
		        formData.add("postcode", "UPD1 1A");
		        formData.add("_csrf", csrfToken);

        client.post().uri("/venues/updateSave/1")
                .header(HttpHeaders.COOKIE, jsessionId)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(containsString("must be a positive integer."));
    }

    @Test
    public void testUpdateVenueSaveValidationFailure() {
        String updateFormPage = client.get()
                .uri("/venues/update/1")
                .header(HttpHeaders.COOKIE, jsessionId)
                .accept(MediaType.TEXT_HTML)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        String csrfToken = extractCsrfToken(updateFormPage);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("name", "");
        formData.add("capacity", "150");
        formData.add("road", "Updated Road");
        formData.add("postcode", "UPD1 1AA");
        formData.add("_csrf", csrfToken);

        client.post().uri("/venues/updateSave/1")
                .header(HttpHeaders.COOKIE, jsessionId)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
		        .value(containsString("The Venue cannot be empty."));
    }

    @Test
    public void testDeleteVenueNotFound() {
        String venuesPage = client.get()
                .uri("/venues")
                .header(HttpHeaders.COOKIE, jsessionId)
                .accept(MediaType.TEXT_HTML)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        String csrfToken = extractCsrfToken(venuesPage);

        client.delete()
              .uri("/venues/99")
              .header(HttpHeaders.COOKIE, jsessionId)
              .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
              .header("X-CSRF-TOKEN", csrfToken)
              .exchange()
              .expectStatus().isNotFound()
              .expectBody(String.class)
              .value(containsString("not found"))
              .value(containsString("Venue"))
              .value(containsString("99"));
    }

    @Test
    public void testDeleteVenueWithEventsFails() {
        String venuePage = client.get()
                 .uri("/venues/1")
              .header(HttpHeaders.COOKIE, jsessionId)
              .accept(MediaType.TEXT_HTML)
              .exchange()
              .expectStatus().isOk()
              .expectBody(String.class)
              .returnResult()
              .getResponseBody();

        String csrf = extractCsrfToken(venuePage);

        client.delete()
                .uri("/venues/1")
                .header(HttpHeaders.COOKIE, jsessionId)
                .header("X-CSRF-TOKEN", csrf)
                .exchange()
                .expectStatus().is3xxRedirection()
                  .expectHeader().valueMatches("Location", ".*/venues/1.*");
    }

    @Test
    public void testDeleteVenueSuccess() {
        String venuePage = client.get()
                 .uri("/venues/4")
              .header(HttpHeaders.COOKIE, jsessionId)
              .accept(MediaType.TEXT_HTML)
              .exchange()
              .expectStatus().isOk()
              .expectBody(String.class)
              .returnResult()
              .getResponseBody();

        String csrf = extractCsrfToken(venuePage);

        client.delete()
                .uri("/venues/4")
                .header(HttpHeaders.COOKIE, jsessionId)
                .header("X-CSRF-TOKEN", csrf)
                .exchange()
                .expectStatus().is3xxRedirection()
                  .expectHeader().valueMatches("Location", ".*/venues.*");
    }

    @Test
    public void testGetVenueEvents() {
        client.get()
                .uri("/venues/1/events")
                .header(HttpHeaders.COOKIE, jsessionId)
                .accept(MediaType.TEXT_HTML)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(containsString("Events"))
                .value(containsString("Venue"));
    }

    @Test
    public void testGetVenueNext3Events() {
        client.get()
                .uri("/venues/1/next3events")
                .header(HttpHeaders.COOKIE, jsessionId)
                .accept(MediaType.TEXT_HTML)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(containsString("Next 3 events"))
                .value(containsString("Venue"));
    }

    @Test
    public void testPostNewVenueWithInvalidCapacity() {
        String newVenuePage = client.get()
                .uri("/venues/new")
                .header(HttpHeaders.COOKIE, jsessionId)
                .accept(MediaType.TEXT_HTML)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        String newCsrfToken = extractCsrfToken(newVenuePage);


        MultiValueMap<String, String> venueForm = new LinkedMultiValueMap<>();
        venueForm.add("name", "New Venue");
        venueForm.add("capacity", "-1");
        venueForm.add("road", "Test Road");
        venueForm.add("postcode", "M2 3AB");
        venueForm.add("_csrf", newCsrfToken);


        client.post().uri("/venues")
              .header(HttpHeaders.COOKIE, jsessionId)
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(BodyInserters.fromFormData(venueForm))
              .exchange()
              .expectStatus().isOk()
              .expectBody(String.class)
              .value(containsString("must be a positive integer."));
    }

    @Test
    public void testPostNewVenueWithMissingRoad() {
        String newVenuePage = client.get()
                .uri("/venues/new")
                .header(HttpHeaders.COOKIE, jsessionId)
                .accept(MediaType.TEXT_HTML)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        String newCsrfToken = extractCsrfToken(newVenuePage);


        MultiValueMap<String, String> venueForm = new LinkedMultiValueMap<>();
        venueForm.add("name", "New Venue");
        venueForm.add("capacity", "500");
        venueForm.add("postcode", "M2 3AB");
        venueForm.add("_csrf", newCsrfToken);

        client.post().uri("/venues")
              .header(HttpHeaders.COOKIE, jsessionId)
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(BodyInserters.fromFormData(venueForm))
              .exchange()
              .expectStatus().isOk()
              .expectBody(String.class)
              .value(containsString("The Road cannot be empty"));
    }

    @Test
    public void testPostNewVenueWithoutAuth() {
        MultiValueMap<String, String> venueForm = new LinkedMultiValueMap<>();
        venueForm.add("name", "New Venue");
        venueForm.add("capacity", "500");
        venueForm.add("road", "Test Road");
        venueForm.add("postcode", "M2 3AB");


        client.post().uri("/venues")
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(BodyInserters.fromFormData(venueForm))
              .exchange()
              .expectStatus().isForbidden();
    }
}
