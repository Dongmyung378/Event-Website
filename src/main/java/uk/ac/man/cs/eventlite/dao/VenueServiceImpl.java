package uk.ac.man.cs.eventlite.dao;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import uk.ac.man.cs.eventlite.entities.Event;
import uk.ac.man.cs.eventlite.entities.Venue;
import uk.ac.man.cs.eventlite.services.Coordinates;
import uk.ac.man.cs.eventlite.services.GeocodingService;
import uk.ac.man.cs.eventlite.services.EventSchedule;
import uk.ac.man.cs.eventlite.services.PageQuery;

@Service
public class VenueServiceImpl implements VenueService {
    @Autowired
    private VenueRepository venueRepository;
    @Autowired
    private GeocodingService geocodingService;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private Clock clock = Clock.systemDefaultZone();

    @Override
    public long count() { return venueRepository.count(); }

    @Override
    public Iterable<Venue> findAll() { return venueRepository.findAllByOrderByNameAsc(); }

    @Override
    public Page<Venue> findPage(String query, int page, int size) {
        PageRequest request = PageQuery.request(page, size);
        String pattern = PageQuery.literalPattern(query);
        return pattern == null ? venueRepository.findPage(request)
            : venueRepository.findNamePage(pattern, request);
    }

    @Override
    public Venue save(Venue venue) {
        Venue stored = venue.getId() == 0 ? null : venueRepository.findById(venue.getId()).orElse(null);
        boolean addressChanged = stored == null
            || !Objects.equals(stored.getRoad(), venue.getRoad())
            || !Objects.equals(stored.getPostcode(), venue.getPostcode());
        if (addressChanged) {
            venue.setLatitude(null);
            venue.setLongitude(null);
        }
        if (addressChanged || venue.getLatitude() == null || venue.getLongitude() == null) {
            if (venue.getRoad() != null && !venue.getRoad().isBlank()
                    && venue.getPostcode() != null && !venue.getPostcode().isBlank()) {
                try {
                    Coordinates coords = geocodingService.getCoordinates(venue.getRoad(), venue.getPostcode());
                    if (coords != null && coords.getLatitude() != null && coords.getLongitude() != null) {
                        venue.setLatitude(coords.getLatitude());
                        venue.setLongitude(coords.getLongitude());
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return venueRepository.save(venue);
    }

    @Override
    public Optional<Venue> findById(long id) { return venueRepository.findById(id); }

    @Override
    public void deleteById(long id) { venueRepository.deleteById(id); }

    @Override
    public boolean existsById(long id) { return venueRepository.existsById(id); }

    @Override
    public List<Venue> findByNameContainingIgnoreCase(String name) {
        return venueRepository.findByNameContainingIgnoreCase(name);
    }

    @Override
    public List<Venue> findTop3Venues() {
        return venueRepository.findMostPopular(PageRequest.of(0, 3));
    }

    @Override
    public List<Event> findNext3Events(long venueId) {
        return eventRepository.findUpcomingAtVenue(venueId, LocalDate.now(clock), PageRequest.of(0, 3));
    }

    public static List<Event> upcomingEvents(List<Event> events) {
        // Retained for legacy callers; request paths use the bounded repository queries above.
        return EventSchedule.upcomingEvents(events, LocalDate.now());
    }
}
