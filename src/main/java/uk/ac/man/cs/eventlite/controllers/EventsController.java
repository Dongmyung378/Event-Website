package uk.ac.man.cs.eventlite.controllers;

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
import uk.ac.man.cs.eventlite.dao.MastodonService;
import uk.ac.man.cs.eventlite.dao.VenueService;
import uk.ac.man.cs.eventlite.entities.Event;
import uk.ac.man.cs.eventlite.entities.Venue;
import uk.ac.man.cs.eventlite.exceptions.EventNotFoundException;
import uk.ac.man.cs.eventlite.services.MutationService;

@Controller
@RequestMapping(value = "/events", produces = MediaType.TEXT_HTML_VALUE)
public class EventsController {
    @InitBinder("event")
    public void configureEventBinding(WebDataBinder binder) {
        binder.setAllowedFields("id", "version", "name", "date", "time", "description", "venue", "venue.id");
    }
    @Autowired
    private EventService eventService;
    @Autowired
    private VenueService venueService;
    @Autowired
    private MastodonService mastodonService;
    @Autowired
    private MutationService mutationService;

    @ExceptionHandler(EventNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String eventNotFoundHandler(EventNotFoundException ex, Model model) {
        model.addAttribute("not_found_id", ex.getId());
        return "events/not_found";
    }

    @GetMapping("/{id}")
    public String getEvent(@PathVariable("id") long id, Model model) {
        Event event = eventService.findById(id).orElseThrow(() -> new EventNotFoundException(id));
        model.addAttribute("event", event);
        model.addAttribute("success", false);
        model.addAttribute("message", "");
        return "events/details";
    }

    @GetMapping
    public String getAllEvents(@RequestParam(required = false) Integer page,
                               @RequestParam(required = false) Integer size, Model model) {
        if (page == null && size == null) populateList(model, eventService.findAll());
        else populatePage(model, null, page, size);
        return "events/index";
    }

    private void populateList(Model model, Iterable<Event> events) {
        model.addAttribute("events", events);
        model.addAttribute("venues", venueService.findAll());
        model.addAttribute("mastodonPosts", mastodonService.getTimeline());
    }

    @GetMapping("/search")
    public String searchEvents(@RequestParam("q") String query,
                               @RequestParam(required = false) Integer page,
                               @RequestParam(required = false) Integer size, Model model) {
        String normalized = query.trim();
        if (page == null && size == null) populateList(model, eventService.findByNameContainingIgnoreCase(normalized));
        else populatePage(model, normalized, page, size);
        model.addAttribute("query", normalized);
        return "events/index";
    }

    private void populatePage(Model model, String query, Integer page, Integer size) {
        Page<Event> result = eventService.findPage(query, page == null ? 0 : page, size == null ? 20 : size);
        populateList(model, result.getContent());
        model.addAttribute("resultPage", result);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String invalidPage(Model model) {
        model.addAttribute("conflictMessage", "Use a page number of zero or greater and a page size from 1 to 100.");
        return "mutation-conflict";
    }

    @GetMapping("/new")
    public String newEvent(Model model) {
        if (!model.containsAttribute("event")) model.addAttribute("event", new Event());
        model.addAttribute("venues", venueService.findAll());
        return "events/new";
    }

    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String createEvent(@Valid @ModelAttribute Event event, BindingResult errors,
                              Model model, RedirectAttributes redirectAttrs) {
        event.setId(0);
        event.setVersion(null);
        if (!errors.hasErrors()) resolveVenue(event, errors);
        if (errors.hasErrors()) {
            model.addAttribute("venues", venueService.findAll());
            return "events/new";
        }
        mutationService.createEvent(event);
        redirectAttrs.addFlashAttribute("ok_message", "New event added.");
        return "redirect:/events";
    }

    @GetMapping("/update/{id}")
    public String updateEvent(@PathVariable("id") long id, Model model) {
        Event event = eventService.findById(id).orElseThrow(() -> new EventNotFoundException(id));
        model.addAttribute("event", event);
        model.addAttribute("venues", venueService.findAll());
        return "events/update";
    }

    @PostMapping("/updateSave/{id}")
    public String updateSave(@PathVariable("id") long id, @Valid @ModelAttribute Event event,
                             BindingResult errors, Model model, RedirectAttributes redirectAttrs) {
        event.setId(id);
        if (!errors.hasErrors()) resolveVenue(event, errors);
        if (errors.hasErrors()) {
            model.addAttribute("venues", venueService.findAll());
            return "events/update";
        }
        mutationService.updateEvent(id, event);
        redirectAttrs.addFlashAttribute("ok_message", "Event updated successfully.");
        return "redirect:/events";
    }

    private void resolveVenue(Event event, BindingResult errors) {
        Venue selected = venueService.findById(event.getVenue().getId()).orElse(null);
        if (selected == null) errors.rejectValue("venue", "venue.notFound", "Select an existing venue.");
        else event.setVenue(selected);
    }

    @DeleteMapping("/{id}")
    public String deleteGreeting(@PathVariable("id") long id, RedirectAttributes redirectAttrs) {
        mutationService.deleteEvent(id);
        redirectAttrs.addFlashAttribute("ok_message", "Event deleted.");
        return "redirect:/events";
    }

    @GetMapping("/{id}/venue")
    public String getEventVenue(@PathVariable("id") long id, Model model) {
        Event event = eventService.findById(id).orElseThrow(() -> new EventNotFoundException(id));
        Venue venue = event.getVenue();
        if (venue == null) {
            model.addAttribute("not_found_id", id);
            return "venues/not_found";
        }
        model.addAttribute("venue", venue);
        model.addAttribute("events", venueService.findNext3Events(venue.getId()));
        return "venues/details";
    }

    @PostMapping("/{id}")
    public String postEvent(@PathVariable("id") long id, @RequestParam("m") String message,
                            Model model, RedirectAttributes redirectAttrs) {
        Event event = eventService.findById(id).orElseThrow(() -> new EventNotFoundException(id));
        model.addAttribute("event", event);
        model.addAttribute("message", message);
        model.addAttribute("success", false);
        if (message.isBlank() || message.codePointCount(0, message.length()) > 500) {
            model.addAttribute("error_message", "Enter a message between 1 and 500 characters.");
            return "events/details";
        }
        try {
            mastodonService.postStatus(message);
            model.addAttribute("success", true);
        } catch (RuntimeException ex) {
            model.addAttribute("error_message", "Social posting is unavailable. Please try again later.");
        }
        return "events/details";
    }
}
