package uk.ac.man.cs.eventlite.dao;

import java.util.List;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import uk.ac.man.cs.eventlite.entities.Venue;

public interface VenueRepository extends CrudRepository<Venue, Long> {
	Iterable<Venue> findAllByOrderByNameAsc();
	List<Venue> findByNameContainingIgnoreCase(String name);

    @Query(value = "select v from Venue v order by case when v.name is null then 1 else 0 end, lower(v.name), v.id",
        countQuery = "select count(v) from Venue v")
    Page<Venue> findPage(Pageable pageable);

    @Query(value = "select v from Venue v where lower(v.name) like :pattern escape '!' order by lower(v.name), v.id",
        countQuery = "select count(v) from Venue v where lower(v.name) like :pattern escape '!'")
    Page<Venue> findNamePage(@Param("pattern") String pattern, Pageable pageable);

    @Query("select v from Venue v left join v.events e group by v "
        + "order by count(e.id) desc, case when v.name is null then 1 else 0 end, lower(v.name), v.id")
    List<Venue> findMostPopular(Pageable pageable);
}
