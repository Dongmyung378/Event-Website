package uk.ac.man.cs.eventlite.services;

import java.util.Locale;
import org.springframework.data.domain.PageRequest;

/** Shared bounds and literal substring semantics for HTML and HAL pagination. */
public final class PageQuery {
    public static final int MAX_SIZE = 100;

    private PageQuery() { }

    public static PageRequest request(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("Page must be zero or greater and size must be between 1 and 100.");
        }
        return PageRequest.of(page, size);
    }

    public static String literalPattern(String query) {
        if (query == null || query.isBlank()) { return null; }
        String literal = query.trim().toLowerCase(Locale.ROOT)
            .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + literal + "%";
    }
}
