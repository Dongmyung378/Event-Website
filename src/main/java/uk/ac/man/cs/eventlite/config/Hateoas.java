package uk.ac.man.cs.eventlite.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.hateoas.server.LinkRelationProvider;
import org.springframework.hateoas.server.core.EvoInflectorLinkRelationProvider;

@Configuration
public class Hateoas {
    /** Only explicit controllers expose data; JPA repositories have no automatic write routes. */
    @Bean
    public LinkRelationProvider linkRelationProvider() {
        return new EvoInflectorLinkRelationProvider();
    }
}
