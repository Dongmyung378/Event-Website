package uk.ac.man.cs.eventlite.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Initializes version counters on pre-upgrade rows after Hibernate adds the columns. */
@Component
@Profile("!test")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class VersionColumnInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public VersionColumnInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        jdbc.update("update events set version = 0 where version is null");
        jdbc.update("update venue set version = 0 where version is null");
    }
}
