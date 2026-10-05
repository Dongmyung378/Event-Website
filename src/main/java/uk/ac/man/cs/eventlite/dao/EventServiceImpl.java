package uk.ac.man.cs.eventlite.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import uk.ac.man.cs.eventlite.entities.Event;

@Service
public class EventServiceImpl implements EventService {
    @Autowired
    private EventRepository eventRepository;

    @Override
    public long count() { return eventRepository.count(); }

    @Override
    public Iterable<Event> findAll() { return eventRepository.findAllByOrderByDateAscNameAsc(); }

    @Override
    public Optional<Event> findById(long id) { return eventRepository.findById(id); }

    @Override
    public Event save(Event event) { return eventRepository.save(event); }

    @Override
    public void deleteById(long id) { eventRepository.deleteById(id); }

    @Override
    public boolean existsById(long id) { return eventRepository.existsById(id); }

    @Override
    public List<Event> findByNameContainingIgnoreCase(String name) {
        return eventRepository.findByNameContainingIgnoreCase(name);
    }

    @Override
    public List<Event> findTop3Events() {
        List<Event> events = new ArrayList<>();
        findAll().forEach(events::add);
        return VenueServiceImpl.upcomingEvents(events);
    }

    @Override
    public List<Event> getEventsByVenueId(long venueId) {
        return eventRepository.findAllByVenueId(venueId);
    }
}
