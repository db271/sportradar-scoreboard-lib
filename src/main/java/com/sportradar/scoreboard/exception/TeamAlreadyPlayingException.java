package com.sportradar.scoreboard.exception;

import java.io.Serial;

/**
 * Thrown when a match is started for a team that is already playing in another match in
 * progress. A team can only play one match at a time.
 */
public class TeamAlreadyPlayingException extends ScoreboardException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String team;

    /**
     * @param team         the team that is already playing
     * @param currentMatch the match in progress the team plays in, for example
     *                     {@code "Mexico 0 – Canada 5"}
     */
    public TeamAlreadyPlayingException(String team, String currentMatch) {
        super("Team '" + team + "' is already playing in a match in progress: " + currentMatch);
        this.team = team;
    }

    /** Returns the name of the team that is already playing. */
    public String getTeam() {
        return team;
    }
}
