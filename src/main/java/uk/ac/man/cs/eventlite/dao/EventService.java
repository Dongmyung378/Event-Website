package uk.ac.man.cs.eventlite.dao;

import uk.ac.man.cs.eventlite.entities.Event;
import uk.ac.man.cs.eventlite.entities.Venue;
import java.util.List;
import java.util.Optional;
public interface EventService {

	long count();

	public boolean existsById(long id);

	Iterable<Event> findAll();

	Event save(Event event);

	List<Event> findByNameContainingIgnoreCase(String name);

	Optional<Event> findById(long id);

	public void deleteById(long id);

	List<Event> getEventsByVenueId(long venueId);

	public List<Event> findTop3Events();
}
