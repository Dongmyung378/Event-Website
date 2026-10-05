package uk.ac.man.cs.eventlite.dao;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import uk.ac.man.cs.eventlite.services.ExternalApiClient;

class MastodonServiceTest {
    private final ExternalApiClient api = mock(ExternalApiClient.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private MastodonService enabled() {
        when(api.timeout()).thenReturn(Duration.ofSeconds(2));
        return new MastodonService(api, mapper, "https://mastodon.example", "test-token", "123");
    }

    @Test
    void unconfiguredServiceMakesNoRequestsAndCannotReportPublishingSuccess() {
        var service = new MastodonService(api, mapper, "", "", "");
        assertFalse(service.isEnabled());
        assertTrue(service.getTimeline().isEmpty());
        assertThrows(MastodonPublishingException.class, () -> service.postStatus("Test post"));
        verifyNoInteractions(api);
    }

    @Test
    void designatedAccountFeedRejectsPrivateAndOtherAccounts() throws Exception {
        var service = enabled();
        HttpResponse<String> response = response(200, """
                [
                  {"content":"Public post","url":"https://mastodon.example/@owner/1","created_at":"2026-01-01T12:00:00Z","visibility":"public","account":{"id":"123"}},
                  {"content":"Private post","url":"https://mastodon.example/@owner/2","created_at":"2026-01-01T12:00:00Z","visibility":"private","account":{"id":"123"}},
                  {"content":"Other account","url":"https://mastodon.example/@other/3","created_at":"2026-01-01T12:00:00Z","visibility":"public","account":{"id":"456"}},
                  {"content":"Malformed date","url":"https://mastodon.example/@owner/4","created_at":"invalid","visibility":"public","account":{"id":"123"}},
                  {"content":"Unsafe link","url":"javascript:alert(1)","created_at":"2026-01-01T12:00:00Z","visibility":"public","account":{"id":"123"}}
                ]
                """);
        when(api.send(any())).thenReturn(response);
        var posts = service.getTimeline();
        assertEquals(1, posts.size());
        assertEquals("Public post", posts.get(0).getContent());
        verify(api).send(argThat(request -> request.uri().getPath().equals("/api/v1/accounts/123/statuses")
                && request.method().equals("GET")));
    }

    @Test
    void httpFailureAndTimeoutPropagateAsSafePublishingFailures() throws Exception {
        var service = enabled();
        doReturn(response(401, "sensitive upstream error")).when(api).send(any());
        var failure = assertThrows(MastodonPublishingException.class, () -> service.postStatus("Test post"));
        assertFalse(failure.getMessage().contains("test-token"));
        assertFalse(failure.getMessage().contains("sensitive"));
        doThrow(new TimeoutException()).when(api).send(any());
        assertThrows(MastodonPublishingException.class, () -> service.postStatus("Test post"));
        assertTrue(service.getTimeline().isEmpty());
    }

    @Test
    void successfulPublishingUsesDesignatedInstanceAndIdempotencyHeader() throws Exception {
        var service = enabled();
        doReturn(response(200, "{}")).when(api).send(any());
        assertDoesNotThrow(() -> service.postStatus("Test post"));
        verify(api).send(argThat(request -> request.uri().toString().equals("https://mastodon.example/api/v1/statuses")
                && request.method().equals("POST") && request.headers().firstValue("Idempotency-Key").isPresent()));
    }

    @Test
    void emojiBoundaryUsesUnicodeCharactersInsteadOfUtf16Units() throws Exception {
        var service = enabled();
        doReturn(response(200, "{}")).when(api).send(any());
        String emoji = new String(Character.toChars(0x1F680));
        assertDoesNotThrow(() -> service.postStatus(emoji.repeat(500)));
        assertThrows(MastodonPublishingException.class, () -> service.postStatus(emoji.repeat(501)));
        verify(api, times(1)).send(any());
    }

    @Test
    void invalidConfigurationAndBlankPostsFailBeforeNetwork() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> new MastodonService(api, mapper, "http://mastodon.example", "test-token", "123"));
        var service = enabled();
        assertThrows(MastodonPublishingException.class, () -> service.postStatus(" "));
        verify(api, never()).send(any());
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }
}
