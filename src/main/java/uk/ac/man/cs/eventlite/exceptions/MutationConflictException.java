package uk.ac.man.cs.eventlite.exceptions;

/** A submitted form can no longer be applied to the record that was displayed. */
public class MutationConflictException extends RuntimeException {
    public MutationConflictException(String message) {
        super(message);
    }
}
