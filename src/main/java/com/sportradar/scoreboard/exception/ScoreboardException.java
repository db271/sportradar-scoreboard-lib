package com.sportradar.scoreboard.exception;

import java.io.Serial;

/**
 * Base class for errors caused by the state of the scoreboard, such as updating a match that
 * isn't in progress. Catch it to handle every scoreboard error in one place.
 *
 * <p>Invalid arguments (blank team names, negative scores) throw
 * {@link IllegalArgumentException} instead.
 */
public abstract class ScoreboardException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    protected ScoreboardException(String message) {
        super(message);
    }
}
