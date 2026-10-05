package uk.ac.man.cs.eventlite.assemblers;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import uk.ac.man.cs.eventlite.controllers.EventsControllerApi;
import uk.ac.man.cs.eventlite.entities.Event;

@Component
public class EventModelAssembler implements RepresentationModelAssembler<Event, EntityModel<Event>> {

	@Override
	public EntityModel<Event> toModel(Event event) {
		EntityModel<Event> model = EntityModel.of(event,
                linkTo(methodOn(EventsControllerApi.class).getEvent(event.getId())).withSelfRel(),
                linkTo(methodOn(EventsControllerApi.class)).withRel("event"));

        if (event.getVenue() != null) {
            model.add(linkTo(methodOn(EventsControllerApi.class).getEventVenue(event.getId())).withRel("venue"));
        }

        return model;
	}
}
