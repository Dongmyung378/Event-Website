package uk.ac.man.cs.eventlite.controllers;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import uk.ac.man.cs.eventlite.dao.EventService;
import uk.ac.man.cs.eventlite.dao.VenueService;
import uk.ac.man.cs.eventlite.dao.VenueServiceImpl;
import uk.ac.man.cs.eventlite.entities.Event;
import uk.ac.man.cs.eventlite.entities.Venue;
import uk.ac.man.cs.eventlite.exceptions.VenueNotFoundException;
@Controller
@RequestMapping(value = "/venues", produces = { MediaType.TEXT_HTML_VALUE })
public class VenuesController {
    @InitBinder("venue")
    public void configureVenueBinding(WebDataBinder binder) {
        binder.setAllowedFields("id", "name", "capacity", "road", "postcode");
    }

	@Autowired
	private EventService eventService;

	@Autowired
	private VenueService venueService;

	@ExceptionHandler(VenueNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public String venueNotFoundHandler(VenueNotFoundException vx, Model model) {
		model.addAttribute("not_found_id", vx.getId());

		return "venues/not_found";
	}

	@GetMapping("/{id}")
	public String getVenue(@PathVariable("id") long id, Model model) {
	    return venueService.findById(id)
	        .map(venue -> {
	            model.addAttribute("venue", venue);
	            model.addAttribute("events", venueService.findNext3Events(id));
	            return "venues/details";
	        })
	        .orElseThrow(() -> new VenueNotFoundException(id));
	}

	@GetMapping
	public String getAllVenues(Model model) {
		model.addAttribute("venues", venueService.findAll());
		return "venues/index";
	}

	@GetMapping("/new")
    public String newVenue(Model model) {
        if (!model.containsAttribute("venue")) {
            model.addAttribute("venue", new Venue());
        }
        return "venues/new";
    }

	@PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String createVenue(@Valid @ModelAttribute Venue venue, BindingResult errors,
                              Model model, RedirectAttributes redirectAttrs) {
        venue.setId(0);
        if (errors.hasErrors()) {
            return "venues/new";
        }
        venueService.save(venue);
        redirectAttrs.addFlashAttribute("ok_message", "New venue added.");
        return "redirect:/venues";
    }

//
	@GetMapping("/search")
	public String searchVenues(@RequestParam("q") String query, Model model) {
	    List<Venue> venues = venueService.findByNameContainingIgnoreCase(query.trim());
		model.addAttribute("venues", venues);
		model.addAttribute("query", query.trim());
	    return "venues/index";
	}
//
//

	@GetMapping("/update/{id}")
	public String updateVenue(@PathVariable("id") long id, Model model) {
        return venueService.findById(id)
            .map(venue -> {
                model.addAttribute("venue", venue);
                return "venues/update";
            })
            .orElseThrow(() -> new VenueNotFoundException(id));
    }

	@PostMapping("/updateSave/{id}")
	public String updateSave(@PathVariable("id") long id,
	                         @Valid @ModelAttribute Venue venue,
	                         BindingResult errors,
	                         Model model,
	                         RedirectAttributes redirectAttrs) {

	    Venue existingVenue = venueService.findById(id)
	        .orElseThrow(() -> new VenueNotFoundException(id));
	    venue.setId(id);
	    if (errors.hasErrors()) {
	        return "venues/update"; // Reload form if errors exist
	    }

	    if (!Objects.equals(existingVenue.getRoad(), venue.getRoad())
	            || !Objects.equals(existingVenue.getPostcode(), venue.getPostcode())) {
	        existingVenue.setLatitude(null);
	        existingVenue.setLongitude(null);
	    }

	    existingVenue.setName(venue.getName());
	    existingVenue.setCapacity(venue.getCapacity());
	    existingVenue.setAddress(venue.getRoad(), venue.getPostcode());

	    venueService.save(existingVenue);

	    redirectAttrs.addFlashAttribute("ok_message", "Venue updated successfully.");
	    return "redirect:/venues";
	}

	@DeleteMapping("/{id}")
	public String deleteVenue(@PathVariable("id") long id, RedirectAttributes redirectAttrs) {
		Venue venue = venueService.findById(id)
			    .orElseThrow(() -> new VenueNotFoundException(id));

		if (!venue.getEvents().isEmpty()) {
	        redirectAttrs.addFlashAttribute("error_message", "Venue cannot be deleted because it has events.");
	        return "redirect:/venues/{id}";
		}
		venueService.deleteById(id);
		redirectAttrs.addFlashAttribute("ok_message", "Venue deleted.");

		return "redirect:/venues";
	}

	@GetMapping("/{id}/events")
	public String getVenueEvents(@PathVariable("id") long id, Model model) {
		Venue venue = venueService.findById(id)
				.orElseThrow(() -> new VenueNotFoundException(id));

		List<Event> events = eventService.getEventsByVenueId(id);

	    events.sort(Comparator
	        .comparing(Event::getDate, Comparator.nullsLast(Comparator.naturalOrder())));

		model.addAttribute("venue", venue);
		model.addAttribute("events", events);

		return "venues/events";
	}

	@GetMapping("/{id}/next3events")
	public String getVenueEvents2(@PathVariable("id") long id, Model model) {
		Venue venue = venueService.findById(id)
				.orElseThrow(() -> new VenueNotFoundException(id));

		List<Event> allEvents = eventService.getEventsByVenueId(id);

		allEvents.sort(Comparator
	        .comparing(Event::getDate, Comparator.nullsLast(Comparator.naturalOrder())));

	    List<Event> limitedEvents = VenueServiceImpl.upcomingEvents(allEvents);

		model.addAttribute("venue", venue);
		model.addAttribute("events", limitedEvents);

		return "venues/next3events";
	}
}
