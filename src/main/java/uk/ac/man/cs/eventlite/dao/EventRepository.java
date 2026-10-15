package uk.ac.man.cs.eventlite.dao;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import uk.ac.man.cs.eventlite.entities.Event;
import uk.ac.man.cs.eventlite.entities.Venue;
import uk.ac.man.cs.eventlite.services.EventSchedule;

public interface EventRepository extends CrudRepository<Event, Long> {
//	public Iterable<Event> findAllByName(String name);
//	public Iterable<Event> findAllByDate(LocalDate date);
//	public Iterable<Event> findAllBVenue(Long venue);
	Iterable<Event> findAllByOrderByDateAscNameAsc();
	List<Event> findByNameContainingIgnoreCase(String name);
	Optional<Event> findById(long id);
    Iterable<Event> findAllByDate(LocalDate date);
    Iterable<Event> findAllByVenue(Venue venue);
    @Query("select e from Event e join fetch e.venue where e.venue.id = :venueId order by "
        + EventSchedule.JPQL_ORDER)
    List<Event> findAllByVenueId(@Param("venueId") Long venueId);

    @Query(value = "select e from Event e join fetch e.venue order by " + EventSchedule.JPQL_ORDER,
        countQuery = "select count(e) from Event e")
    Page<Event> findPage(Pageable pageable);

    @Query(value = "select e from Event e join fetch e.venue where lower(e.name) like :pattern escape '!' order by "
        + EventSchedule.JPQL_ORDER,
        countQuery = "select count(e) from Event e where lower(e.name) like :pattern escape '!'")
    Page<Event> findNamePage(@Param("pattern") String pattern, Pageable pageable);

    @Query("select e from Event e join fetch e.venue where e.date >= :today order by "
        + EventSchedule.JPQL_ORDER)
    List<Event> findUpcoming(@Param("today") LocalDate today, Pageable pageable);

    @Query("select e from Event e join fetch e.venue where e.venue.id = :venueId and e.date >= :today order by "
        + EventSchedule.JPQL_ORDER)
    List<Event> findUpcomingAtVenue(@Param("venueId") long venueId, @Param("today") LocalDate today,
        Pageable pageable);
}
