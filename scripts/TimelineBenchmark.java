package uk.ac.man.cs.eventlite.benchmark;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLSession;
import uk.ac.man.cs.eventlite.dao.MastodonService;
import uk.ac.man.cs.eventlite.services.ExternalApiClient;

/** Measures the actual service against a deterministic delayed, in-process transport. */
public class TimelineBenchmark {
    private static final String BODY = """
        [{"content":"Controlled public feed","url":"https://mastodon.example/@fixture/1",
          "created_at":"2026-01-01T12:00:00Z","visibility":"public","account":{"id":"123"}}]
        """;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("Usage: TimelineBenchmark output.json source-label [delay-ms]");
        int delayMillis = args.length > 2 ? Integer.parseInt(args[2]) : 150;
        if (delayMillis < 1 || delayMillis > 1000) throw new IllegalArgumentException("Use delay 1..1000 ms.");
        var transport = new DelayedTransport(delayMillis);
        // Jackson's package changed between the initial and supported Spring Boot releases.
        // Use the service's declared mapper type so both revisions share this exact harness.
        var constructor = java.util.Arrays.stream(MastodonService.class.getConstructors())
                .filter(candidate -> candidate.getParameterCount() == 5
                        && candidate.getParameterTypes()[0] == ExternalApiClient.class)
                .findFirst().orElseThrow();
        var mapperType = constructor.getParameterTypes()[1];
        var mapper = mapperType.getConstructor().newInstance();
        var service = (MastodonService) constructor.newInstance(transport, mapper,
                "https://mastodon.example", "fixture-token", "123");
        var samples = new ArrayList<Double>();
        try {
            for (int call = 0; call < 21; call++) {
                long started = System.nanoTime();
                var statuses = service.getTimeline();
                samples.add((System.nanoTime() - started) / 1_000_000.0);
                if (statuses.size() != 1 || !"Controlled public feed".equals(statuses.get(0).getContent())) {
                    throw new IllegalStateException("Feed results changed.");
                }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("format", 1);
            result.put("source", args[1]);
            result.put("method", "actual MastodonService, delayed in-process HTTP transport, no external network");
            result.put("injected_delay_ms", delayMillis);
            result.put("page_feed_calls", samples.size());
            result.put("external_transport_calls", transport.calls);
            result.put("cold_ms", samples.get(0));
            result.put("all_p50_ms", percentile(samples, .5));
            result.put("all_p95_ms", percentile(samples, .95));
            result.put("warm_p50_ms", percentile(samples.subList(1, samples.size()), .5));
            result.put("warm_p95_ms", percentile(samples.subList(1, samples.size()), .95));
            result.put("total_ms", samples.stream().mapToDouble(Double::doubleValue).sum());
            result.put("samples_ms", samples);
            Path output = Path.of(args[0]).toAbsolutePath();
            Files.createDirectories(output.getParent());
            String json = (String) mapperType.getMethod("writeValueAsString", Object.class).invoke(mapper, result);
            Files.writeString(output, json + System.lineSeparator());
            System.out.println(json);
        } finally {
            try { service.getClass().getMethod("stopRefresh").invoke(service); }
            catch (NoSuchMethodException ignored) { /* Initial implementation has no cache executor. */ }
        }
    }

    private static double percentile(List<Double> values, double fraction) {
        double[] sorted = values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        return sorted[Math.max(0, (int) Math.ceil(sorted.length * fraction) - 1)];
    }

    private static final class DelayedTransport extends ExternalApiClient {
        private final int delayMillis;
        private int calls;
        private DelayedTransport(int delayMillis) {
            // send() is overridden; no network client or sockets are created.
            super(null, Duration.ofSeconds(3));
            this.delayMillis = delayMillis;
        }
        @Override
        public HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException, TimeoutException {
            calls++;
            Thread.sleep(delayMillis);
            return new HttpResponse<>() {
                public int statusCode() { return 200; }
                public HttpRequest request() { return request; }
                public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
                public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (name, value) -> true); }
                public String body() { return BODY; }
                public Optional<SSLSession> sslSession() { return Optional.empty(); }
                public URI uri() { return request.uri(); }
                public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
            };
        }
    }
}
