package uk.ac.man.cs.eventlite.dao;

/** Contains only a safe user-facing message, never an API response or credentials. */
public class MastodonPublishingException extends RuntimeException {
    public MastodonPublishingException(String message) {
        super(message);
    }
}
