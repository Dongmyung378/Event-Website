package uk.ac.man.cs.eventlite.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class GeocodingServiceTest {
    private final ExternalApiClient api = mock(ExternalApiClient.class);

    @Test
    void noTokenMeansNoRequest() throws Exception {
        var coordinates = new GeocodingService(api, new ObjectMapper(), "").getCoordinates("Road", "M1 1AA");
        assertNull(coordinates.getLatitude());
        assertNull(coordinates.getLongitude());
        verifyNoInteractions(api);
    }

    @Test
    void validCoordinatesAreParsedInCorrectOrder() throws Exception {
        when(api.timeout()).thenReturn(Duration.ofSeconds(2));
        doReturn(response(200, "{\"features\":[{\"geometry\":{\"coordinates\":[-2.23,53.46]}}]}")).when(api).send(any());
        var coordinates = new GeocodingService(api, new ObjectMapper(), "test-token").getCoordinates("Road", "M1 1AA");
        assertEquals(53.46, coordinates.getLatitude());
        assertEquals(-2.23, coordinates.getLongitude());
        verify(api).send(argThat(request -> request.uri().getRawQuery().contains("q=Road%2C%20M1%201AA")
                && request.uri().getQuery().contains("permanent=true")));
    }

    @Test
    void timeoutAndInvalidResponsesReturnEmptyCoordinates() throws Exception {
        when(api.timeout()).thenReturn(Duration.ofSeconds(2));
        var service = new GeocodingService(api, new ObjectMapper(), "test-token");
        doThrow(new TimeoutException()).when(api).send(any());
        assertNull(service.getCoordinates("Road", "M1").getLatitude());
        doReturn(response(200, "{\"features\":[{\"geometry\":{\"coordinates\":[999,53]}}]}")).when(api).send(any());
        assertNull(service.getCoordinates("Road", "M1").getLongitude());
        doReturn(response(200, "{\"features\":[]}")).when(api).send(any());
        assertNull(service.getCoordinates("Road", "M1").getLatitude());
        doReturn(response(200, "")).when(api).send(any());
        assertNull(service.getCoordinates("Road", "M1").getLatitude());
        doReturn(response(403, "secret upstream failure")).when(api).send(any());
        assertNull(service.getCoordinates("Road", "M1").getLatitude());
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }
}
