package uk.ac.man.cs.eventlite.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class ExternalApiClientTest {
    @Test
    void timedOutRequestIsCancelled() {
        HttpClient client = mock(HttpClient.class);
        CompletableFuture<HttpResponse<String>> pending = new CompletableFuture<>();
        when(client.<String>sendAsync(any(), any())).thenReturn(pending);
        var api = new ExternalApiClient(client, Duration.ofMillis(10));
        var request = HttpRequest.newBuilder(URI.create("https://offline.example")).GET().build();
        assertThrows(TimeoutException.class, () -> api.send(request));
        assertTrue(pending.isCancelled());
    }

    @Test
    void interruptedRequestIsCancelledAndInterruptionPropagates() {
        HttpClient client = mock(HttpClient.class);
        CompletableFuture<HttpResponse<String>> pending = new CompletableFuture<>();
        when(client.<String>sendAsync(any(), any())).thenReturn(pending);
        var api = new ExternalApiClient(client, Duration.ofSeconds(2));
        var request = HttpRequest.newBuilder(URI.create("https://offline.example")).GET().build();
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class, () -> api.send(request));
            assertTrue(pending.isCancelled());
        } finally {
            Thread.interrupted();
        }
    }
}
