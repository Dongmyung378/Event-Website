package uk.ac.man.cs.eventlite.services;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GeocodingService {
    private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);
    private final ExternalApiClient apiClient;
    private final ObjectMapper mapper;
    private final String token;

    public GeocodingService(ExternalApiClient apiClient, ObjectMapper mapper,
                            @Value("${eventlite.mapbox.token:}") String token) {
        this.apiClient = apiClient;
        this.mapper = mapper;
        this.token = token.strip();
    }

    public Coordinates getCoordinates(String road, String postcode) throws InterruptedException {
        if (token.isBlank() || road == null || road.isBlank() || postcode == null || postcode.isBlank()) {
            return new Coordinates();
        }
        String query = URLEncoder.encode(road + ", " + postcode, StandardCharsets.UTF_8).replace("+", "%20");
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        // Venue coordinates are stored in the database, so request permanent geocoding results.
        URI uri = URI.create("https://api.mapbox.com/search/geocode/v6/forward?q=" + query
                + "&limit=1&autocomplete=false&permanent=true&access_token=" + encodedToken);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(apiClient.timeout())
                .header("Accept", "application/json").GET().build();
        try {
            HttpResponse<String> response = apiClient.send(request);
            if (response.statusCode() != 200) {
                log.warn("Geocoding failed with HTTP status {}.", response.statusCode());
                return new Coordinates();
            }
            JsonNode result = mapper.readTree(response.body());
            if (result == null) { return new Coordinates(); }
            JsonNode center = result.path("features").path(0).path("geometry").path("coordinates");
            if (center.size() != 2 || !center.path(0).isNumber() || !center.path(1).isNumber()) {
                return new Coordinates();
            }
            double longitude = center.get(0).asDouble();
            double latitude = center.get(1).asDouble();
            if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                    || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                return new Coordinates();
            }
            return new Coordinates(latitude, longitude);
        } catch (IOException | TimeoutException ex) {
            // Do not print exception messages: request URLs contain a token and an address.
            log.warn("Geocoding unavailable or timed out.");
            return new Coordinates();
        }
    }
}
