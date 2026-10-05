package uk.ac.man.cs.eventlite.controllers;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import uk.ac.man.cs.eventlite.assemblers.VenueModelAssembler;
import uk.ac.man.cs.eventlite.assemblers.EventModelAssembler;
import uk.ac.man.cs.eventlite.dao.VenueService;
import uk.ac.man.cs.eventlite.dao.EventService;
import uk.ac.man.cs.eventlite.entities.Event;
import uk.ac.man.cs.eventlite.entities.Venue;
import uk.ac.man.cs.eventlite.exceptions.EventNotFoundException;
import uk.ac.man.cs.eventlite.exceptions.VenueNotFoundException;

@RestController
@RequestMapping(value = "/api/venues", produces = { MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE })
public class VenuesControllerApi {

	private static final String NOT_FOUND_MSG = "{ \"error\": \"%s\", \"id\": %d }";

	@Autowired
	private VenueService venueService;

	@Autowired
	private EventService eventService;

	@Autowired
	private VenueModelAssembler venueAssembler;

	@Autowired
	private EventModelAssembler eventAssembler;

	@ExceptionHandler(VenueNotFoundException.class)
	public ResponseEntity<?> venueNotFoundHandler(VenueNotFoundException vx) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(String.format(NOT_FOUND_MSG, vx.getMessage(), vx.getId()));
	}

	@GetMapping("/{id}")
	public EntityModel<Venue> getVenue(@PathVariable("id") long id) {
		Venue venue = venueService.findById(id).orElseThrow(() -> new VenueNotFoundException(id));
	    return venueAssembler.toModel(venue);
	}

	@GetMapping
	public CollectionModel<EntityModel<Venue>> getAllVenues() {
		CollectionModel<EntityModel<Venue>> collectionModel = venueAssembler.toCollectionModel(venueService.findAll())
				.add(linkTo(methodOn(VenuesControllerApi.class).getAllVenues()).withSelfRel());

		return collectionModel;
	}

	@GetMapping("/{id}/events")
	public CollectionModel<EntityModel<Event>> getVenueEvents(@PathVariable("id") long id) {
		venueService.findById(id).orElseThrow(() -> new VenueNotFoundException(id));

		List<Event> events = eventService.getEventsByVenueId(id);
		events.sort(Comparator
		        .comparing(Event::getDate, Comparator.nullsLast(Comparator.naturalOrder())));

		List<EntityModel<Event>> eventModels = events.stream()
                .map(eventAssembler::toModel)
                .collect(Collectors.toList());

		return CollectionModel.of(eventModels,
                WebMvcLinkBuilder.linkTo(WebMvcLinkBuilder.methodOn(VenuesControllerApi.class)
                        .getVenueEvents(id)).withSelfRel());
	}

	@GetMapping("/{id}/next3events")
	public CollectionModel<EntityModel<Event>> getVenueNext3events(@PathVariable("id") long id) {
		venueService.findById(id).orElseThrow(() -> new VenueNotFoundException(id));

		List<Event> allEvents = eventService.getEventsByVenueId(id);

		allEvents.sort(Comparator
	        .comparing(Event::getDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Event::getTime, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Event::getName, Comparator.nullsLast(Comparator.naturalOrder())));

	    List<Event> next3Events = allEvents.stream()
	        .filter(e -> e.getDate() != null && !e.getDate().isBefore(LocalDate.now()))
	        .limit(3)
	        .collect(Collectors.toList());

	    List<EntityModel<Event>> eventModels = next3Events.stream()
                .map(eventAssembler::toModel)
                .collect(Collectors.toList());

	    return CollectionModel.of(eventModels,
                WebMvcLinkBuilder.linkTo(WebMvcLinkBuilder.methodOn(VenuesControllerApi.class)
                        .getVenueNext3events(id)).withSelfRel());
	}
}
