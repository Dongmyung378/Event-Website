package uk.ac.man.cs.eventlite.dao;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import uk.ac.man.cs.eventlite.entities.Event;
import uk.ac.man.cs.eventlite.entities.Venue;
import uk.ac.man.cs.eventlite.services.Coordinates;
import uk.ac.man.cs.eventlite.services.GeocodingService;

@Service
public class VenueServiceImpl implements VenueService {
    @Autowired
    private VenueRepository venueRepository;
    @Autowired
    private GeocodingService geocodingService;

    @Override
    public long count() { return venueRepository.count(); }

    @Override
    public Iterable<Venue> findAll() { return venueRepository.findAllByOrderByNameAsc(); }

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
        List<Venue> venues = new ArrayList<>();
        findAll().forEach(venues::add);
        return venues.stream()
            .sorted(Comparator.comparingInt((Venue venue) -> venue.getEvents().size()).reversed()
                .thenComparing(Venue::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
            .limit(3)
            .collect(Collectors.toList());
    }

    @Override
    public List<Event> findNext3Events(long venueId) {
        return venueRepository.findById(venueId)
            .map(venue -> upcomingEvents(venue.getEvents()))
            .orElseGet(Collections::emptyList);
    }

    public static List<Event> upcomingEvents(List<Event> events) {
        LocalDate today = LocalDate.now();
        return events.stream()
            .filter(event -> event.getDate() != null && !event.getDate().isBefore(today))
            .sorted(Comparator.comparing(Event::getDate)
                .thenComparing(Event::getTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Event::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
            .limit(3)
            .collect(Collectors.toList());
    }
}
