package uk.ac.man.cs.eventlite.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

class TestPersistenceIsolationTest {

    @Test
    void closingOneContextPreservesAnotherContextsSchemaAndRows() {
        try (var survivor = persistenceContext()) {
            var retiring = persistenceContext();
            JdbcTemplate jdbc = jdbc(survivor);
            try {
                jdbc.update("insert into venue (id, name) values (100, 'Surviving venue')");
            } finally {
                retiring.close();
            }

            // Hibernate create-drop in the closed context must not drop these tables.
            assertEquals(0L, jdbc.queryForObject("select count(*) from events", Long.class));
            assertEquals("Surviving venue",
                    jdbc.queryForObject("select name from venue where id=100", String.class));
        }
    }

    @Test
    void openingAnotherContextDoesNotRecreateAnExistingContextsData() {
        try (var existing = persistenceContext()) {
            JdbcTemplate jdbc = jdbc(existing);
            jdbc.update("insert into venue (id, name) values (100, 'Existing venue')");

            try (var fresh = persistenceContext()) {
                assertEquals("Existing venue",
                        jdbc.queryForObject("select name from venue where id=100", String.class));
                assertEquals(0L, jdbc(fresh).queryForObject("select count(*) from venue", Long.class));
            }
        }
    }

    private static AnnotationConfigApplicationContext persistenceContext() {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles("test");
        context.register(TestPersistence.class);
        context.refresh();
        return context;
    }

    private static JdbcTemplate jdbc(AnnotationConfigApplicationContext context) {
        return new JdbcTemplate(context.getBean(DataSource.class));
    }
}
