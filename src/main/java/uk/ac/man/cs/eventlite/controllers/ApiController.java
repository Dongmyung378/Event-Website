package uk.ac.man.cs.eventlite.controllers;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Discovery for the intentionally read-only API. */
@RestController
public class ApiController {
    @GetMapping(value = {"/api", "/api/"}, produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE})
    public RepresentationModel<?> getApi() {
        return new RepresentationModel<>()
            .add(linkTo(methodOn(ApiController.class).getApi()).withSelfRel())
            .add(linkTo(methodOn(EventsControllerApi.class).getAllEvents()).withRel("events"))
            .add(linkTo(methodOn(VenuesControllerApi.class).getAllVenues()).withRel("venues"));
    }
}
