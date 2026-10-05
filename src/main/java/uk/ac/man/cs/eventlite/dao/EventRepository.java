package uk.ac.man.cs.eventlite.dao;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

import uk.ac.man.cs.eventlite.entities.Event;
import uk.ac.man.cs.eventlite.entities.Venue;

public interface EventRepository extends CrudRepository<Event, Long> {
//	public Iterable<Event> findAllByName(String name);
//	public Iterable<Event> findAllByDate(LocalDate date);
//	public Iterable<Event> findAllBVenue(Long venue);
	Iterable<Event> findAllByOrderByDateAscNameAsc();
	List<Event> findByNameContainingIgnoreCase(String name);
	Optional<Event> findById(long id);
    Iterable<Event> findAllByDate(LocalDate date);
    Iterable<Event> findAllByVenue(Venue venue);
    List<Event> findAllByVenueId(Long venueId);
}
