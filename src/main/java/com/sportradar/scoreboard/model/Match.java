package com.sportradar.scoreboard.model;

/**
 * An immutable snapshot of a match in progress.
 *
 * <p>Team names are kept as given (trimmed) for display. Use {@link #id()} to compare matches.
 *
 * @param homeTeam   name of the home team
 * @param awayTeam   name of the away team
 * @param homeScore  goals scored by the home team, never negative
 * @param awayScore  goals scored by the away team, never negative
 * @param startOrder position in the order matches were started; a higher value means the match
 *                   started later
 */
public record Match(String homeTeam, String awayTeam, int homeScore, int awayScore, long startOrder) {

    /**
     * Validates and trims the team names and checks the scores.
     *
     * @throws IllegalArgumentException if a team name is {@code null} or blank, both names refer to
     *                                  the same team, or a score is negative
     */
    public Match {
        homeTeam = TeamNames.requireValid(homeTeam, "homeTeam");
        awayTeam = TeamNames.requireValid(awayTeam, "awayTeam");
        MatchId.of(homeTeam, awayTeam); // rejects a team playing against itself
        requireNonNegative(homeScore, "homeScore");
        requireNonNegative(awayScore, "awayScore");
    }

    /**
     * Creates a newly started match with a score of 0–0.
     *
     * @throws IllegalArgumentException if a team name is {@code null} or blank, or both names refer
     *                                  to the same team
     */
    public static Match start(String homeTeam, String awayTeam, long startOrder) {
        return new Match(homeTeam, awayTeam, 0, 0, startOrder);
    }

    /**
     * Returns a copy of this match with the given score.
     *
     * @throws IllegalArgumentException if a score is negative
     */
    public Match withScore(int newHomeScore, int newAwayScore) {
        return new Match(homeTeam, awayTeam, newHomeScore, newAwayScore, startOrder);
    }

    /**
     * Returns the sum of both scores. It is a {@code long}, so it cannot overflow.
     */
    public long totalScore() {
        return (long) homeScore + awayScore;
    }

    /**
     * Returns the id of this match.
     */
    public MatchId id() {
        return MatchId.of(homeTeam, awayTeam);
    }

    /**
     * Returns the match in scoreboard format, for example {@code "Mexico 0 – Canada 5"}.
     */
    @Override
    public String toString() {
        return homeTeam + " " + homeScore + " – " + awayTeam + " " + awayScore;
    }

    private static void requireNonNegative(int score, String parameterName) {
        if (score < 0) {
            throw new IllegalArgumentException(parameterName + " must not be negative, was " + score);
        }
    }
}
