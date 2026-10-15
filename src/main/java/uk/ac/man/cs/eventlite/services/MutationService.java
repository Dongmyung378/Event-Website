package uk.ac.man.cs.eventlite.services;

import java.util.Objects;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.ac.man.cs.eventlite.dao.EventRepository;
import uk.ac.man.cs.eventlite.dao.VenueRepository;
import uk.ac.man.cs.eventlite.dao.VenueService;
import uk.ac.man.cs.eventlite.entities.Event;
import uk.ac.man.cs.eventlite.entities.Venue;
import uk.ac.man.cs.eventlite.exceptions.EventNotFoundException;
import uk.ac.man.cs.eventlite.exceptions.MutationConflictException;
import uk.ac.man.cs.eventlite.exceptions.VenueNotFoundException;

/** Keeps each user mutation and its relationship/version checks in one transaction. */
@Service
public class MutationService {
    private final EventRepository events;
    private final VenueRepository venues;
    private final VenueService venueService;
    @PersistenceContext
    private EntityManager entityManager;

    public MutationService(EventRepository events, VenueRepository venues,
                           VenueService venueService) {
        this.events = events;
        this.venues = venues;
        this.venueService = venueService;
    }

    @Transactional
    public Event createEvent(Event submitted) {
        submitted.setId(0);
        submitted.setVersion(null);
        submitted.setVenue(lockSelectedVenue(submitted.getVenue()));
        Event saved = events.save(submitted);
        entityManager.flush();
        return saved;
    }

    @Transactional
    public Event updateEvent(long id, Event submitted) {
        Event existing = events.findById(id).orElseThrow(() -> new EventNotFoundException(id));
        checkVersion(existing.getVersion(), submitted.getVersion());
        Venue selected = lockSelectedVenue(submitted.getVenue());
        existing.setName(submitted.getName());
        existing.setDate(submitted.getDate());
        existing.setTime(submitted.getTime());
        existing.setDescription(submitted.getDescription());
        existing.setVenue(selected);
        // JPA's version predicate also detects a competing commit after the check.
        entityManager.flush();
        return existing;
    }

    @Transactional
    public void deleteEvent(long id) {
        Event existing = events.findById(id).orElseThrow(() -> new EventNotFoundException(id));
        events.delete(existing);
        entityManager.flush();
    }

    @Transactional
    public Venue createVenue(Venue submitted) {
        submitted.setId(0);
        submitted.setVersion(null);
        Venue saved = venueService.save(submitted);
        entityManager.flush();
        return saved;
    }

    @Transactional
    public Venue updateVenue(long id, Venue submitted) {
        Venue existing = venues.findById(id).orElseThrow(() -> new VenueNotFoundException(id));
        checkVersion(existing.getVersion(), submitted.getVersion());
        if (!Objects.equals(existing.getRoad(), submitted.getRoad())
                || !Objects.equals(existing.getPostcode(), submitted.getPostcode())) {
            existing.setLatitude(null);
            existing.setLongitude(null);
        }
        existing.setName(submitted.getName());
        existing.setCapacity(submitted.getCapacity());
        existing.setAddress(submitted.getRoad(), submitted.getPostcode());
        venueService.save(existing);
        entityManager.flush();
        return existing;
    }

    @Transactional
    public boolean deleteVenue(long id) {
        Venue existing = entityManager.find(Venue.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (existing == null) throw new VenueNotFoundException(id);
        Long count = entityManager.createQuery(
                "select count(e) from Event e where e.venue.id = :id", Long.class)
            .setParameter("id", id).getSingleResult();
        if (count > 0) return false;
        venues.delete(existing);
        entityManager.flush();
        return true;
    }

    private Venue lockSelectedVenue(Venue selected) {
        Venue current = selected == null ? null
            : entityManager.find(Venue.class, selected.getId(), LockModeType.PESSIMISTIC_WRITE);
        if (current == null) {
            throw new MutationConflictException("The selected venue is no longer available. Reload the form and select another venue.");
        }
        return current;
    }

    private void checkVersion(Long current, Long submitted) {
        if (submitted == null || !Objects.equals(current, submitted)) {
            throw new MutationConflictException("This record has changed since the form was opened. Reload the latest version before saving again.");
        }
    }
}
