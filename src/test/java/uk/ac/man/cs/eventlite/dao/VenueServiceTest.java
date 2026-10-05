package uk.ac.man.cs.eventlite.dao;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import uk.ac.man.cs.eventlite.EventLite;
import uk.ac.man.cs.eventlite.dao.VenueService;
import uk.ac.man.cs.eventlite.entities.Venue;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = EventLite.class)
@DirtiesContext
@ActiveProfiles("test")
public class VenueServiceTest {

	@Autowired
	private VenueService venueService;

	@Test
	@Transactional
	public void testSaveVenue() {
	    Venue venue = new Venue();
	    venue.setName("Test Venue");
	    Venue saved = venueService.save(venue);
	    assertNotNull(saved.getId(), "Saved venue should have an ID");
	}
}
