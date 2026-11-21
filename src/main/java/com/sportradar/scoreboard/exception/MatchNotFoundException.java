package com.sportradar.scoreboard.exception;

import java.io.Serial;

/**
 * Thrown when an operation refers to a match that is not in progress: it was never started,
 * or it has already finished.
 */
public class MatchNotFoundException extends ScoreboardException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String homeTeam;
    private final String awayTeam;

    public MatchNotFoundException(String homeTeam, String awayTeam) {
        super("No match in progress between '" + homeTeam + "' and '" + awayTeam + "'");
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
    }

    /** Returns the home team name as given by the caller. */
    public String getHomeTeam() {
        return homeTeam;
    }

    /** Returns the away team name as given by the caller. */
    public String getAwayTeam() {
        return awayTeam;
    }
}
