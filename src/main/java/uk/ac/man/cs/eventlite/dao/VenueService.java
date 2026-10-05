package uk.ac.man.cs.eventlite.dao;

import java.util.List;
import java.util.Optional;

import uk.ac.man.cs.eventlite.entities.Event;
import uk.ac.man.cs.eventlite.entities.Venue;

public interface VenueService {

	public boolean existsById(long id);

	public long count();

	public Venue save(Venue venue);

	public Iterable<Venue> findAll();

	Optional<Venue> findById(long id);

	public void deleteById(long id);

	List<Venue> findByNameContainingIgnoreCase(String name);

	public List<Venue> findTop3Venues();

	List<Event> findNext3Events(long venueId);

}
