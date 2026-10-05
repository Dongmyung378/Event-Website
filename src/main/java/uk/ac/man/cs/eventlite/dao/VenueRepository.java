package uk.ac.man.cs.eventlite.dao;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import uk.ac.man.cs.eventlite.entities.Venue;

public interface VenueRepository extends CrudRepository<Venue, Long> {
	Iterable<Venue> findAllByOrderByNameAsc();
	List<Venue> findByNameContainingIgnoreCase(String name);
}
