package uk.ac.man.cs.eventlite.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
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
import uk.ac.man.cs.eventlite.entities.Event;
import uk.ac.man.cs.eventlite.entities.Venue;
import uk.ac.man.cs.eventlite.exceptions.VenueNotFoundException;
import uk.ac.man.cs.eventlite.services.MutationService;
@Controller
@RequestMapping(value = "/venues", produces = { MediaType.TEXT_HTML_VALUE })
public class VenuesController {
    @InitBinder("venue")
    public void configureVenueBinding(WebDataBinder binder) {
        binder.setAllowedFields("id", "version", "name", "capacity", "road", "postcode");
    }

	@Autowired
	private EventService eventService;

	@Autowired
	private VenueService venueService;

    @Autowired
    private MutationService mutationService;

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
	public String getAllVenues(@RequestParam(required = false) Integer page,
                               @RequestParam(required = false) Integer size, Model model) {
        if (page == null && size == null) model.addAttribute("venues", venueService.findAll());
        else populatePage(model, null, page, size);
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
        venue.setVersion(null);
        if (errors.hasErrors()) {
            return "venues/new";
        }
        mutationService.createVenue(venue);
        redirectAttrs.addFlashAttribute("ok_message", "New venue added.");
        return "redirect:/venues";
    }

//
	@GetMapping("/search")
	public String searchVenues(@RequestParam("q") String query,
                               @RequestParam(required = false) Integer page,
                               @RequestParam(required = false) Integer size, Model model) {
        if (page == null && size == null) model.addAttribute("venues", venueService.findByNameContainingIgnoreCase(query.trim()));
        else populatePage(model, query.trim(), page, size);
		model.addAttribute("query", query.trim());
	    return "venues/index";
	}

    private void populatePage(Model model, String query, Integer page, Integer size) {
        Page<Venue> result = venueService.findPage(query, page == null ? 0 : page, size == null ? 20 : size);
        model.addAttribute("venues", result.getContent());
        model.addAttribute("resultPage", result);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String invalidPage(Model model) {
        model.addAttribute("conflictMessage", "Use a page number of zero or greater and a page size from 1 to 100.");
        return "mutation-conflict";
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

	    venue.setId(id);
	    if (errors.hasErrors()) {
	        return "venues/update"; // Reload form if errors exist
	    }

	    mutationService.updateVenue(id, venue);

	    redirectAttrs.addFlashAttribute("ok_message", "Venue updated successfully.");
	    return "redirect:/venues";
	}

	@DeleteMapping("/{id}")
	public String deleteVenue(@PathVariable("id") long id, RedirectAttributes redirectAttrs) {
		if (!mutationService.deleteVenue(id)) {
	        redirectAttrs.addFlashAttribute("error_message", "Venue cannot be deleted because it has events.");
	        return "redirect:/venues/{id}";
		}
		redirectAttrs.addFlashAttribute("ok_message", "Venue deleted.");

		return "redirect:/venues";
	}

	@GetMapping("/{id}/events")
	public String getVenueEvents(@PathVariable("id") long id, Model model) {
		Venue venue = venueService.findById(id)
				.orElseThrow(() -> new VenueNotFoundException(id));

		List<Event> events = eventService.getEventsByVenueId(id);

		model.addAttribute("venue", venue);
		model.addAttribute("events", events);

		return "venues/events";
	}

	@GetMapping("/{id}/next3events")
	public String getVenueEvents2(@PathVariable("id") long id, Model model) {
		Venue venue = venueService.findById(id)
				.orElseThrow(() -> new VenueNotFoundException(id));

	    List<Event> limitedEvents = venueService.findNext3Events(id);

		model.addAttribute("venue", venue);
		model.addAttribute("events", limitedEvents);

		return "venues/next3events";
	}
}
