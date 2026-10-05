package uk.ac.man.cs.eventlite.services;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Bounded requests, without automatic cross-host redirects or credential-bearing error logs. */
@Component
public class ExternalApiClient {
    private final HttpClient client;
    private final Duration timeout;

    @Autowired
    public ExternalApiClient(@Value("${eventlite.integrations.timeout-millis:3000}") long timeoutMillis) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER).build(), Duration.ofMillis(timeoutMillis));
    }

    public ExternalApiClient(HttpClient client, Duration timeout) {
        if (timeout.toMillis() < 1 || timeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("External API timeout must be between 1 and 30000 milliseconds.");
        }
        this.client = client;
        this.timeout = timeout;
    }

    public Duration timeout() {
        return timeout;
    }

    public HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException, TimeoutException {
        CompletableFuture<HttpResponse<String>> pending = client.sendAsync(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        try {
            return pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException | InterruptedException ex) {
            pending.cancel(true);
            throw ex;
        } catch (ExecutionException ex) {
            throw new IOException("External API request failed.");
        }
    }
}
