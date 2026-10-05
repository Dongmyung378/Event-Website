package uk.ac.man.cs.eventlite.dao;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.ac.man.cs.eventlite.services.ExternalApiClient;

@Service
public class MastodonService {
    private static final Logger log = LoggerFactory.getLogger(MastodonService.class);
    private final URI instance;
    private final String token;
    private final String accountId;
    private final ExternalApiClient apiClient;
    private final ObjectMapper mapper;

    public MastodonService(ExternalApiClient apiClient, ObjectMapper mapper,
                           @Value("${eventlite.mastodon.instance:}") String instance,
                           @Value("${eventlite.mastodon.token:}") String token,
                           @Value("${eventlite.mastodon.account-id:}") String accountId) {
        this.apiClient = apiClient;
        this.mapper = mapper;
        this.instance = parseInstance(instance);
        this.token = token.strip();
        this.accountId = accountId.strip();
        if (this.token.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Mastodon token must not contain control characters.");
        }
    }

    private static URI parseInstance(String value) {
        if (value.isBlank()) { return null; }
        try {
            URI uri = URI.create(value.strip());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Mastodon instance must be an HTTPS origin.");
        }
    }

    public boolean isEnabled() {
        return instance != null && !token.isBlank() && accountId.matches("[0-9]+");
    }

    public void postStatus(String content) {
        if (!isEnabled()) {
            throw new MastodonPublishingException("Social publishing is not configured.");
        }
        if (content == null || content.isBlank() || content.codePointCount(0, content.length()) > 500) {
            throw new MastodonPublishingException("A social post must contain between 1 and 500 characters.");
        }
        try {
            String body = "status=" + URLEncoder.encode(content, StandardCharsets.UTF_8) + "&visibility=public";
            HttpRequest request = HttpRequest.newBuilder(instance.resolve("/api/v1/statuses"))
                    .timeout(apiClient.timeout()).header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/x-www-form-urlencoded").header("Accept", "application/json")
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = apiClient.send(request);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Mastodon publishing failed with HTTP status {}.", response.statusCode());
                throw new MastodonPublishingException("The social post could not be published. Please try again later.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MastodonPublishingException("Social publishing was interrupted.");
        } catch (IOException | TimeoutException ex) {
            log.warn("Mastodon publishing failed or timed out.");
            throw new MastodonPublishingException("The social post could not be published. Please try again later.");
        }
    }

    public List<MastodonStatus> getTimeline() {
        if (!isEnabled()) { return List.of(); }
        try {
            HttpRequest request = HttpRequest.newBuilder(instance.resolve("/api/v1/accounts/" + accountId
                            + "/statuses?limit=20&exclude_replies=true&exclude_reblogs=true"))
                    .timeout(apiClient.timeout()).header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json").GET().build();
            HttpResponse<String> response = apiClient.send(request);
            if (response.statusCode() != 200) {
                log.warn("Mastodon feed failed with HTTP status {}.", response.statusCode());
                return List.of();
            }
            List<MastodonStatus> statuses = mapper.readValue(response.body(), new TypeReference<>() { });
            if (statuses == null) { return List.of(); }
            return statuses.stream().filter(this::isPublicAccountStatus).limit(3).toList();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (IOException | TimeoutException ex) {
            log.warn("Mastodon feed unavailable or timed out.");
            return List.of();
        }
    }

    private boolean isPublicAccountStatus(MastodonStatus status) {
        if (status == null || !"public".equals(status.getVisibility()) || status.getAccount() == null
                || !accountId.equals(status.getAccount().getId()) || status.getContent() == null
                || status.getCreatedAt() == null || status.getUrl() == null) {
            return false;
        }
        try {
            Instant.parse(status.getCreatedAt());
            URI url = URI.create(status.getUrl());
            return "https".equalsIgnoreCase(url.getScheme()) && url.getHost() != null && url.getUserInfo() == null;
        } catch (DateTimeParseException | IllegalArgumentException ex) {
            return false;
        }
    }
}
