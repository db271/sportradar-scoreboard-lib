package com.sportradar.scoreboard.model;

/**
 * Identifies a match by its home and away teams.
 *
 * <p>Team names are normalised (trimmed and lower-cased), so {@code MatchId.of("Spain", "Brazil")}
 * equals {@code MatchId.of(" spain", "BRAZIL ")}. The order of the teams matters:
 * Spain–Brazil and Brazil–Spain are different ids.
 *
 * @param homeTeamKey normalised name of the home team
 * @param awayTeamKey normalised name of the away team
 */
public record MatchId(String homeTeamKey, String awayTeamKey) {

    /**
     * Normalises both team names.
     *
     * @throws IllegalArgumentException if a name is {@code null} or blank, or both names refer to
     *                                  the same team
     */
    public MatchId {
        homeTeamKey = TeamNames.key(homeTeamKey, "homeTeam");
        awayTeamKey = TeamNames.key(awayTeamKey, "awayTeam");
        if (homeTeamKey.equals(awayTeamKey)) {
            throw new IllegalArgumentException("A team cannot play against itself: " + homeTeamKey);
        }
    }

    /**
     * Creates the id of the match between the given teams.
     *
     * @throws IllegalArgumentException if a name is {@code null} or blank, or both names refer to
     *                                  the same team
     */
    public static MatchId of(String homeTeam, String awayTeam) {
        return new MatchId(homeTeam, awayTeam);
    }

    /**
     * Returns {@code true} if the two matches have at least one team in common, whether as home
     * or away team.
     */
    public boolean sharesTeamWith(MatchId other) {
        return involves(other.homeTeamKey) || involves(other.awayTeamKey);
    }

    /**
     * Returns {@code true} if the team with the given normalised name plays in this match.
     */
    public boolean involves(String teamKey) {
        return homeTeamKey.equals(teamKey) || awayTeamKey.equals(teamKey);
    }
}
