package uk.ac.man.cs.eventlite.benchmark;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

import uk.ac.man.cs.eventlite.EventLite;
import uk.ac.man.cs.eventlite.dao.EventService;
import uk.ac.man.cs.eventlite.dao.VenueService;
import uk.ac.man.cs.eventlite.entities.Event;
import uk.ac.man.cs.eventlite.entities.Venue;

/** Opt-in service-query measurement. It never connects to a configured user database. */
public class QueryBenchmark {
    private static final int VENUES = 100;
    private static final int WARMUPS = 5;
    private static final int SAMPLES = 21;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("Usage: QueryBenchmark output.json source-label [1000,10000,100000]");
        Path output = Path.of(args[0]).toAbsolutePath();
        int[] sizes = args.length > 2 ? Arrays.stream(args[2].split(",")).mapToInt(Integer::parseInt).toArray()
                : new int[] {1000, 10000, 100000};
        for (int size : sizes) if (size < 100 || size > 100000) throw new IllegalArgumentException("Use 100..100000 events.");
        SpringApplication application = new SpringApplication(EventLite.class);
        try (ConfigurableApplicationContext context = application.run(
                "--spring.profiles.active=benchmark", "--server.port=0", "--server.address=127.0.0.1",
                "--spring.datasource.url=jdbc:h2:mem:eventlite-query-benchmark;DB_CLOSE_DELAY=-1",
                "--spring.datasource.username=h2", "--spring.datasource.password=",
                "--spring.jpa.hibernate.ddl-auto=create-drop",
                "--spring.jpa.properties.hibernate.generate_statistics=true",
                "--spring.jpa.show-sql=false", "--spring.main.banner-mode=off",
                "--eventlite.admin.username=", "--eventlite.admin.password=",
                "--eventlite.mapbox.token=", "--eventlite.mapbox.public-token=",
                "--eventlite.mastodon.instance=", "--eventlite.mastodon.token=", "--eventlite.mastodon.account-id=",
                "--logging.level.root=ERROR", "--logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF")) {
            EventService events = context.getBean(EventService.class);
            VenueService venues = context.getBean(VenueService.class);
            DataSource dataSource = context.getBean(DataSource.class);
            Statistics statistics = context.getBean(EntityManagerFactory.class).unwrap(SessionFactory.class).getStatistics();
            TransactionTemplate transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            transaction.setReadOnly(true);
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            List<Map<String, Object>> rows = new ArrayList<>();
            LocalDate fixtureDay = LocalDate.now().plusDays(1);
            for (int size : sizes) {
                seed(dataSource, size, fixtureDay);
                rows.add(measure("next-three-events", size, transaction, statistics,
                        () -> eventSnapshot(events.findTop3Events())));
                rows.add(measure("popular-three-venues", size, transaction, statistics,
                        () -> venueSnapshot(venues.findTop3Venues())));
                rows.add(measure("venue-next-three-events", size, transaction, statistics,
                        () -> eventSnapshot(venues.findNext3Events(1))));
                rows.add(measure("legacy-full-event-list", size, transaction, statistics,
                        () -> eventSnapshot(events.findAll())));
                output.getParent().toFile().mkdirs();
                Map<String, Object> report = new LinkedHashMap<>();
                report.put("schema", "eventlite-query-benchmark/v1");
                report.put("source", args[1]);
                report.put("java", System.getProperty("java.version"));
                report.put("os", System.getProperty("os.name"));
                report.put("availableProcessors", Runtime.getRuntime().availableProcessors());
                report.put("maxHeapBytes", Runtime.getRuntime().maxMemory());
                report.put("fixture", Map.of("venues", VENUES, "eventSizes", sizes,
                        "firstEventDate", fixtureDay.toString(), "distribution", "round-robin venues; date=(index%30); null time every seventh event; unique lowercase padded names"));
                report.put("warmups", WARMUPS);
                report.put("samples", SAMPLES);
                report.put("scope", "Sequential in-process service queries, fresh transaction/persistence context per sample; H2 in-memory only. Not HTTP throughput or production latency.");
                report.put("rows", rows);
                Files.writeString(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report), StandardCharsets.UTF_8);
                System.out.println("Measured " + size + " events; report: " + output);
            }
        }
    }

    private static Map<String, Object> measure(String operation, int size, TransactionTemplate transaction,
            Statistics statistics, Supplier<List<String>> query) throws Exception {
        String expected = null;
        List<Double> times = new ArrayList<>();
        List<Long> statements = new ArrayList<>(), entities = new ArrayList<>();
        int returned = 0;
        for (int sample = -WARMUPS; sample < SAMPLES; sample++) {
            statistics.clear();
            long started = System.nanoTime();
            List<String> result = transaction.execute(status -> query.get());
            double elapsed = (System.nanoTime() - started) / 1000000.0;
            String signature = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(String.join("\n", result).getBytes(StandardCharsets.UTF_8)));
            if (expected != null && !expected.equals(signature)) throw new IllegalStateException("Non-deterministic output: " + operation);
            expected = signature;
            returned = result.size();
            if (sample >= 0) {
                times.add(elapsed);
                statements.add(statistics.getPrepareStatementCount());
                entities.add(statistics.getEntityLoadCount());
            }
        }
        List<Double> sorted = times.stream().sorted().toList();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("operation", operation); row.put("events", size); row.put("venues", VENUES);
        row.put("returnedRows", returned); row.put("resultSha256", expected);
        row.put("medianMs", sorted.get(sorted.size() / 2));
        row.put("p95Ms", sorted.get((int)Math.ceil(sorted.size() * .95) - 1));
        row.put("minSqlStatements", statements.stream().mapToLong(Long::longValue).min().orElseThrow());
        row.put("maxSqlStatements", statements.stream().mapToLong(Long::longValue).max().orElseThrow());
        row.put("minLoadedEntities", entities.stream().mapToLong(Long::longValue).min().orElseThrow());
        row.put("maxLoadedEntities", entities.stream().mapToLong(Long::longValue).max().orElseThrow());
        row.put("samplesMs", times); row.put("samplesSqlStatements", statements); row.put("samplesLoadedEntities", entities);
        return row;
    }

    private static List<String> eventSnapshot(Iterable<Event> source) {
        List<String> rows = new ArrayList<>();
        for (Event event : source) rows.add(event.getName() + "|" + event.getDate() + "|" + event.getTime()
                + "|" + (event.getVenue() == null ? "" : event.getVenue().getName()));
        return rows;
    }

    private static List<String> venueSnapshot(Iterable<Venue> source) {
        List<String> rows = new ArrayList<>();
        for (Venue venue : source) rows.add(venue.getName());
        return rows;
    }

    private static void seed(DataSource source, int size, LocalDate fixtureDay) throws Exception {
        try (Connection connection = source.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("delete from events");
                statement.executeUpdate("delete from venue");
            }
            boolean venueVersion = hasVersion(connection, "VENUE"), eventVersion = hasVersion(connection, "EVENTS");
            try (PreparedStatement insert = connection.prepareStatement("insert into venue(id,name,capacity,road,postcode"
                    + (venueVersion ? ",version" : "") + ") values(?,?,?,?,?" + (venueVersion ? ",0" : "") + ")")) {
                for (int id = 1; id <= VENUES; id++) {
                    insert.setLong(1, id); insert.setString(2, String.format("venue-%03d", id));
                    insert.setInt(3, 1000); insert.setString(4, "Fixture road"); insert.setString(5, "M1 1AA");
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            try (PreparedStatement insert = connection.prepareStatement("insert into events(id,name,date,time,description,venue_id"
                    + (eventVersion ? ",version" : "") + ") values(?,?,?,?,?,?" + (eventVersion ? ",0" : "") + ")")) {
                for (int id = 1; id <= size; id++) {
                    insert.setLong(1, id); insert.setString(2, String.format("event-%06d", id));
                    insert.setObject(3, fixtureDay.plusDays(id % 30));
                    insert.setObject(4, id % 7 == 0 ? null : LocalTime.of(10, id % 60));
                    insert.setString(5, "Generated query benchmark fixture"); insert.setLong(6, (id - 1) % VENUES + 1);
                    insert.addBatch();
                    if (id % 1000 == 0) insert.executeBatch();
                }
                if (size % 1000 != 0) insert.executeBatch();
            }
            connection.commit();
        }
    }

    private static boolean hasVersion(Connection connection, String table) throws Exception {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, table, "VERSION")) {
            return columns.next();
        }
    }
}
