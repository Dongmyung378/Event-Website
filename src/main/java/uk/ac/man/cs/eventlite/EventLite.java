package uk.ac.man.cs.eventlite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "uk.ac.man.cs.eventlite")
public class EventLite {

	public static void main(String[] args) {
		SpringApplication.run(EventLite.class, args);
	}

}
