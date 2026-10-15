package uk.ac.man.cs.eventlite.services;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import uk.ac.man.cs.eventlite.entities.Event;

/** The shared ordering of schedules, including a stable ID tie-breaker. */
public final class EventSchedule {
    public static final String JPQL_ORDER = "case when e.date is null then 1 else 0 end, e.date, "
        + "case when e.time is null then 1 else 0 end, e.time, "
        + "case when e.name is null then 1 else 0 end, lower(e.name), e.id";

    public static final Comparator<Event> ORDER = Comparator
        .comparing(Event::getDate, Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(Event::getTime, Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(Event::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
        .thenComparingLong(Event::getId);

    private EventSchedule() { }

    public static List<Event> upcomingEvents(List<Event> events, LocalDate today) {
        return events.stream()
            .filter(event -> event.getDate() != null && !event.getDate().isBefore(today))
            .sorted(ORDER).limit(3).toList();
    }
}
