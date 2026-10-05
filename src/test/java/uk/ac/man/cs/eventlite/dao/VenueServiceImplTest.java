package uk.ac.man.cs.eventlite.dao;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import uk.ac.man.cs.eventlite.entities.Event;
import uk.ac.man.cs.eventlite.entities.Venue;

public class VenueServiceImplTest {

    private VenueRepository venueRepository;
    private VenueServiceImpl venueService;

    @BeforeEach
    public void setup() throws Exception {
        venueRepository = mock(VenueRepository.class);
        venueService = new VenueServiceImpl();
        Field field = VenueServiceImpl.class.getDeclaredField("venueRepository");
        field.setAccessible(true);
        field.set(venueService, venueRepository);
    }

    @Test
    public void testdeleteById() {
        Venue venue = new Venue();
        venue.setId(1L);
        venue.setCapacity(1000);
        venueRepository.save(venue);

        venueService.deleteById(1L);

        verify(venueRepository, times(1)).deleteById(1L);
        Optional<Venue> deletedVenue = venueRepository.findById(1L);
        assertTrue(deletedVenue.isEmpty());
    }
    @Test
    public void testExistsById() {
        when(venueRepository.existsById(1L)).thenReturn(true);
        assertTrue(venueService.existsById(1L));
        verify(venueRepository, times(1)).existsById(1L);

        when(venueRepository.existsById(2L)).thenReturn(false);
        assertFalse(venueService.existsById(2L));
        verify(venueRepository, times(1)).existsById(2L);
    }

    @Test
    public void testFindByNameContainingIgnoreCase() {
        Venue venue1 = new Venue();
        venue1.setId(1L);
        venue1.setName("The Grand Hall");

        Venue venue2 = new Venue();
        venue2.setId(2L);
        venue2.setName("Grand Theatre");

        List<Venue> expectedVenues = Arrays.asList(venue1, venue2);
        when(venueRepository.findByNameContainingIgnoreCase("Grand")).thenReturn(expectedVenues);

        List<Venue> actualVenues = venueService.findByNameContainingIgnoreCase("Grand");
        assertEquals(expectedVenues, actualVenues);
        verify(venueRepository, times(1)).findByNameContainingIgnoreCase("Grand");
    }
}
