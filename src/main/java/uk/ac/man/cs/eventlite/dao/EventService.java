package uk.ac.man.cs.eventlite.dao;

import uk.ac.man.cs.eventlite.entities.Event;
import uk.ac.man.cs.eventlite.entities.Venue;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
public interface EventService {

	long count();

	public boolean existsById(long id);

	Iterable<Event> findAll();

    /** Zero-based page with 1..100 results, optionally filtered by a literal name substring. */
    Page<Event> findPage(String query, int page, int size);

	Event save(Event event);

	List<Event> findByNameContainingIgnoreCase(String name);

	Optional<Event> findById(long id);

	public void deleteById(long id);

	List<Event> getEventsByVenueId(long venueId);

	public List<Event> findTop3Events();
}
