package uk.ac.man.cs.eventlite.config.data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import uk.ac.man.cs.eventlite.dao.EventService;
import uk.ac.man.cs.eventlite.dao.VenueService;
import uk.ac.man.cs.eventlite.entities.Event;
import uk.ac.man.cs.eventlite.entities.Venue;

@Configuration
@Profile("demo")
public class InitialDataLoader {
    @Autowired
    private EventService eventService;
    @Autowired
    private VenueService venueService;

    @Bean
    CommandLineRunner initDatabase() {
        return args -> {
            List<Venue> venues = new ArrayList<>();
            venueService.findAll().forEach(venues::add);
            if (venues.isEmpty()) {
                venues.add(createVenue("Etihad Stadium", "Etihad Stadium, Manchester", "M11 3FF", 1000));
                venues.add(createVenue("Royal Albert Hall", "Royal Albert Hall, South Kensington, London", "SW7 2AP", 1000));
                venues.add(createVenue("Eiffel Tower", "Paris, France", "75007", 1243));
                venues.add(createVenue("Edinburgh Castle", "Castlehill, Edinburgh", "EH1 2NG", 1234));
            }
            if (eventService.count() != 0) return;
            Venue first = venues.get(0);
            Venue second = venues.size() > 1 ? venues.get(1) : first;
            Venue third = venues.size() > 2 ? venues.get(2) : first;
            LocalDate today = LocalDate.now();
            createEvent("Event Alpha", today.plusDays(1), "12:30", second, "This is first event");
            createEvent("Event Beta", today.plusDays(1), "10:00", first, "This is second Event");
            createEvent("Event Apple", today.plusDays(2), null, first, "This is third event");
            createEvent("Event Former", today.minusDays(2), "11:00", second, "This is previous first event");
            createEvent("Event Previous", today.minusDays(2), "18:30", first, "This is previous second event");
            createEvent("Event Past", today.minusDays(3), "17:00", first, "This is previous third event");
            createEvent("Event Theta", today.plusDays(3), "11:23", third, "This is a new event");
        };
    }

    private Venue createVenue(String name, String road, String postcode, int capacity) {
        Venue venue = new Venue();
        venue.setName(name);
        venue.setAddress(road, postcode);
        venue.setCapacity(capacity);
        return venueService.save(venue);
    }

    private void createEvent(String name, LocalDate date, String time, Venue venue, String description) {
        Event event = new Event();
        event.setName(name);
        event.setDate(date);
        event.setTime(time == null ? null : LocalTime.parse(time));
        event.setVenue(venue);
        event.setDescription(description);
        eventService.save(event);
    }
}
