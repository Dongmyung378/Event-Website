package uk.ac.man.cs.eventlite.dao;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import uk.ac.man.cs.eventlite.entities.Event;
import uk.ac.man.cs.eventlite.services.PageQuery;

@Service
public class EventServiceImpl implements EventService {
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private Clock clock = Clock.systemDefaultZone();

    @Override
    public long count() { return eventRepository.count(); }

    @Override
    public Iterable<Event> findAll() { return eventRepository.findAllByOrderByDateAscNameAsc(); }

    @Override
    public Page<Event> findPage(String query, int page, int size) {
        PageRequest request = PageQuery.request(page, size);
        String pattern = PageQuery.literalPattern(query);
        return pattern == null ? eventRepository.findPage(request)
            : eventRepository.findNamePage(pattern, request);
    }

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
        return eventRepository.findUpcoming(LocalDate.now(clock), PageRequest.of(0, 3));
    }

    @Override
    public List<Event> getEventsByVenueId(long venueId) {
        return eventRepository.findAllByVenueId(venueId);
    }
}
