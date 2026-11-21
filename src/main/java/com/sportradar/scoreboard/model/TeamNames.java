package com.sportradar.scoreboard.model;

import java.util.Locale;

/**
 * Validation and normalisation of team names, shared by {@link Match} and {@link MatchId}.
 */
final class TeamNames {

    private TeamNames() {
    }

    /**
     * Returns the team name with leading and trailing whitespace removed.
     *
     * @throws IllegalArgumentException if the name is {@code null} or blank
     */
    static String requireValid(String teamName, String parameterName) {
        if (teamName == null || teamName.isBlank()) {
            throw new IllegalArgumentException(parameterName + " must not be null or blank");
        }
        return teamName.strip();
    }

    /**
     * Returns the key used to compare team names: trimmed and lower-cased, so that
     * {@code "Spain"} and {@code " spain "} refer to the same team.
     *
     * @throws IllegalArgumentException if the name is {@code null} or blank
     */
    static String key(String teamName, String parameterName) {
        return requireValid(teamName, parameterName).toLowerCase(Locale.ROOT);
    }
}
