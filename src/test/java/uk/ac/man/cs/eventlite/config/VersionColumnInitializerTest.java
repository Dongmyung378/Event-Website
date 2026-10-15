package uk.ac.man.cs.eventlite.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class VersionColumnInitializerTest {
    @Test
    void upgradesLegacyRowsPreservesVersionsAndDataAndIsRepeatable() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:eventlite-version-upgrade;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("create table events (id bigint primary key, name varchar(255), version bigint)");
        jdbc.execute("create table venue (id bigint primary key, name varchar(255), version bigint)");
        jdbc.update("insert into events values (1, 'Original event', null), (2, 'Edited event', 7)");
        jdbc.update("insert into venue values (1, 'Original venue', null), (2, 'Edited venue', 4)");
        VersionColumnInitializer initializer = new VersionColumnInitializer(jdbc);
        initializer.run(null);
        initializer.run(null);
        assertEquals(0L, jdbc.queryForObject("select version from events where id=1", Long.class));
        assertEquals(7L, jdbc.queryForObject("select version from events where id=2", Long.class));
        assertEquals(0L, jdbc.queryForObject("select version from venue where id=1", Long.class));
        assertEquals(4L, jdbc.queryForObject("select version from venue where id=2", Long.class));
        assertEquals("Original event", jdbc.queryForObject("select name from events where id=1", String.class));
        assertEquals("Original venue", jdbc.queryForObject("select name from venue where id=1", String.class));
        assertEquals(2L, jdbc.queryForObject("select count(*) from events", Long.class));
        assertEquals(2L, jdbc.queryForObject("select count(*) from venue", Long.class));
    }
}
