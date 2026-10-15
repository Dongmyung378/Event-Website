package uk.ac.man.cs.eventlite.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.HiddenHttpMethodFilter;
import org.springframework.boot.servlet.filter.OrderedHiddenHttpMethodFilter;

@Configuration
public class Forms {

	@Bean
	HiddenHttpMethodFilter hiddenHttpMethodFilter() {
		// Security must authorize the effective DELETE method from rendered forms.
		return new OrderedHiddenHttpMethodFilter();
	}
}
