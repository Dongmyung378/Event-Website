package uk.ac.man.cs.eventlite.controllers;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.MediaTypes;
import org.springframework.data.domain.Page;
import org.springframework.hateoas.PagedModel;
import org.springframework.hateoas.Link;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Map;

import uk.ac.man.cs.eventlite.assemblers.EventModelAssembler;
import uk.ac.man.cs.eventlite.assemblers.VenueModelAssembler;
import uk.ac.man.cs.eventlite.dao.EventService;
import uk.ac.man.cs.eventlite.entities.Event;
import uk.ac.man.cs.eventlite.entities.Venue;
import uk.ac.man.cs.eventlite.exceptions.EventNotFoundException;

@RestController
@RequestMapping(value = "/api/events", produces = { MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE })
public class EventsControllerApi {

	private static final String NOT_FOUND_MSG = "{ \"error\": \"%s\", \"id\": %d }";

	@Autowired
	private EventService eventService;

	@Autowired
	private EventModelAssembler eventAssembler;

	@Autowired
	private VenueModelAssembler venueAssembler;

	@ExceptionHandler(EventNotFoundException.class)
	public ResponseEntity<?> eventNotFoundHandler(EventNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(String.format(NOT_FOUND_MSG, ex.getMessage(), ex.getId()));
	}

	@GetMapping("/{id}")
	public EntityModel<Event> getEvent(@PathVariable("id") long id) {
		Event event = eventService.findById(id).orElseThrow(() -> new EventNotFoundException(id));
	    return eventAssembler.toModel(event);
	}

	@GetMapping(params = {"!page", "!size"})
	public CollectionModel<EntityModel<Event>> getAllEvents() {
		return eventAssembler.toCollectionModel(eventService.findAll())
				.add(linkTo(methodOn(EventsControllerApi.class).getAllEvents()).withSelfRel());
	}

    @GetMapping
    public PagedModel<EntityModel<Event>> getPagedEvents(@RequestParam(required = false) Integer page,
                                                        @RequestParam(required = false) Integer size,
                                                        @RequestParam(required = false) String q) {
        Page<Event> result = eventService.findPage(q, page == null ? 0 : page, size == null ? 20 : size);
        PagedModel<EntityModel<Event>> model = PagedModel.of(
            result.getContent().stream().map(eventAssembler::toModel).toList(),
            new PagedModel.PageMetadata(result.getSize(), result.getNumber(), result.getTotalElements(), result.getTotalPages()));
        model.add(pageLink(result.getNumber(), result.getSize()).withSelfRel());
        if (result.hasPrevious()) model.add(pageLink(result.getNumber() - 1, result.getSize()).withRel("prev"));
        if (result.hasNext()) model.add(pageLink(result.getNumber() + 1, result.getSize()).withRel("next"));
        return model;
    }

    private Link pageLink(int page, int size) {
        return Link.of(ServletUriComponentsBuilder.fromCurrentRequest().replaceQueryParam("page", page)
            .replaceQueryParam("size", size).build(true).toUriString());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> invalidPage() {
        return ResponseEntity.badRequest().body(Map.of("error", "Use page >= 0 and size between 1 and 100."));
    }

	@GetMapping("/{id}/venue")
	public EntityModel<Venue> getEventVenue(@PathVariable("id") long id) {
	    Event event = eventService.findById(id).orElseThrow(() -> new EventNotFoundException(id));
	    Venue venue = event.getVenue();
	    if (venue == null) {
	        throw new EventNotFoundException(id);
	    }
	    return venueAssembler.toModel(venue);
	}
}
