package uk.ac.man.cs.eventlite.config.data;

import java.time.LocalDate;
import java.time.LocalTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@Profile("test")
public class TestDataLoader {

	private final static Logger log = LoggerFactory.getLogger(TestDataLoader.class);

	@Autowired
	private EventService eventService;

	@Autowired
	private VenueService venueService;

	@Bean
	CommandLineRunner initDatabase() {
		return args -> {
			 Venue venue1 = new Venue();

            venue1.setName("Venue A");
            venue1.setCapacity(1000);
            venue1.setAddress("Etihad", "M1");

            Venue venue2 = new Venue();

            venue2.setName("Venue B");
            venue2.setCapacity(1000);

            Venue venue3 = new Venue();

            venue3.setName("Venue C");
            venue3.setCapacity(1243);

            Venue venue4 = new Venue();

            venue4.setName("Venue D");
            venue4.setCapacity(1234);

            venue1 = venueService.save(venue1);
            venue2 = venueService.save(venue2);
            venue3 = venueService.save(venue3);
            venue4 = venueService.save(venue4);

            Event event1 = new Event();
            event1.setName("Event Alpha");
            event1.setDate(LocalDate.now().plusDays(30));
            event1.setTime(LocalTime.parse("12:30"));
            event1.setVenue(venue2);
            event1.setDescription("This is first event");

            Event event2 = new Event();
            event2.setName("Event Beta");
            event2.setDate(LocalDate.now().plusDays(30));
            event2.setTime(LocalTime.parse("10:00"));
            event2.setVenue(venue1);
            event2.setDescription("This is second Event");

            Event event3 = new Event();
            event3.setName("Event Apple");
            event3.setDate(LocalDate.now().plusDays(31));
            event3.setVenue(venue1);
            event3.setDescription("This is third event");

            Event event7 = new Event();
            event7.setName("Event Theta");
            event7.setDate(LocalDate.now().plusDays(32));
            event7.setTime(LocalTime.parse("11:23"));
            event7.setVenue(venue3);
            event7.setDescription("This is a new event");

            Event event4 = new Event();
            event4.setName("Event Former");
            event4.setDate(LocalDate.now().minusDays(2));
            event4.setTime(LocalTime.parse("11:00"));
            event4.setVenue(venue2);
            event4.setDescription("This is previous first event");

            Event event5 = new Event();
            event5.setName("Event Previous");
            event5.setDate(LocalDate.now().minusDays(2));
            event5.setTime(LocalTime.parse("18:30"));
            event5.setVenue(venue1);
            event5.setDescription("This is previous second event");

            Event event6 = new Event();
            event6.setName("Event Past");
            event6.setDate(LocalDate.now().minusDays(3));
            event6.setTime(LocalTime.parse("17:00"));
            event6.setVenue(venue1);
            event6.setDescription("This is previous third event");

            eventService.save(event1);
            eventService.save(event2);
            eventService.save(event3);
            eventService.save(event4);
            eventService.save(event5);
            eventService.save(event6);
            eventService.save(event7);
		};
	}
}
