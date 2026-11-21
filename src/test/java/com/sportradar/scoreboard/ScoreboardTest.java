package com.sportradar.scoreboard;

import com.sportradar.scoreboard.exception.MatchNotFoundException;
import com.sportradar.scoreboard.exception.TeamAlreadyPlayingException;
import com.sportradar.scoreboard.model.Match;
import com.sportradar.scoreboard.repository.InMemoryMatchRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoreboardTest {

    private final Scoreboard scoreboard = new Scoreboard();

    @Test
    void exampleScenarioFromTheTask() {
        startWithScore("Mexico", "Canada", 0, 5);
        startWithScore("Spain", "Brazil", 10, 2);
        startWithScore("Germany", "France", 2, 2);
        startWithScore("Uruguay", "Italy", 6, 6);
        startWithScore("Argentina", "Australia", 3, 1);

        assertThat(summaryLines()).containsExactly(
                "Uruguay 6 – Italy 6",
                "Spain 10 – Brazil 2",
                "Mexico 0 – Canada 5",
                "Argentina 3 – Australia 1",
                "Germany 2 – France 2");
    }

    @Nested
    class StartMatch {

        @Test
        void addsMatchAtZeroZero() {
            Match match = scoreboard.startMatch("Mexico", "Canada");

            assertThat(match).hasToString("Mexico 0 – Canada 0");
            assertThat(scoreboard.getSummary()).containsExactly(match);
        }

        @Test
        void givesLaterMatchesAHigherStartOrder() {
            Match first = scoreboard.startMatch("Mexico", "Canada");
            Match second = scoreboard.startMatch("Spain", "Brazil");

            assertThat(second.startOrder()).isGreaterThan(first.startOrder());
        }

        @Test
        void rejectsSameMatchStartedTwice() {
            scoreboard.startMatch("Mexico", "Canada");

            assertThatThrownBy(() -> scoreboard.startMatch(" mexico ", "CANADA"))
                    .isInstanceOf(TeamAlreadyPlayingException.class)
                    .hasMessageContaining("mexico")
                    .hasMessageContaining("Mexico 0 – Canada 0");
        }

        @Test
        void rejectsReversedFixtureWhileOriginalIsLive() {
            scoreboard.startMatch("Mexico", "Canada");

            assertThatThrownBy(() -> scoreboard.startMatch("Canada", "Mexico"))
                    .isInstanceOf(TeamAlreadyPlayingException.class);
        }

        @Test
        void rejectsHomeTeamAlreadyPlayingElsewhere() {
            scoreboard.startMatch("Mexico", "Canada");

            assertThatThrownBy(() -> scoreboard.startMatch("Canada", "Spain"))
                    .isInstanceOfSatisfying(TeamAlreadyPlayingException.class,
                            e -> assertThat(e.getTeam()).isEqualTo("Canada"));
        }

        @Test
        void rejectsAwayTeamAlreadyPlayingElsewhere() {
            scoreboard.startMatch("Mexico", "Canada");

            assertThatThrownBy(() -> scoreboard.startMatch("Spain", "Mexico"))
                    .isInstanceOfSatisfying(TeamAlreadyPlayingException.class,
                            e -> assertThat(e.getTeam()).isEqualTo("Mexico"));
        }

        @Test
        void allowsTeamToPlayAgainAfterItsMatchFinished() {
            scoreboard.startMatch("Mexico", "Canada");
            scoreboard.finishMatch("Mexico", "Canada");

            Match rematch = scoreboard.startMatch("Canada", "Mexico");

            assertThat(scoreboard.getSummary()).containsExactly(rematch);
        }

        @Test
        void rejectsInvalidTeams() {
            assertThatIllegalArgumentException().isThrownBy(() -> scoreboard.startMatch(null, "Canada"));
            assertThatIllegalArgumentException().isThrownBy(() -> scoreboard.startMatch("Mexico", " "));
            assertThatIllegalArgumentException().isThrownBy(() -> scoreboard.startMatch("Mexico", "MEXICO"));
            assertThat(scoreboard.getSummary()).isEmpty();
        }
    }

    @Nested
    class UpdateScore {

        @Test
        void setsAbsoluteScore() {
            scoreboard.startMatch("Mexico", "Canada");

            Match updated = scoreboard.updateScore("Mexico", "Canada", 0, 5);

            assertThat(updated).hasToString("Mexico 0 – Canada 5");
            assertThat(scoreboard.getSummary()).containsExactly(updated);
        }

        @Test
        void matchesTeamsIgnoringCaseAndWhitespace() {
            scoreboard.startMatch("Mexico", "Canada");

            Match updated = scoreboard.updateScore(" MEXICO", "canada ", 1, 0);

            assertThat(updated).hasToString("Mexico 1 – Canada 0");
        }

        @Test
        void acceptsLowerScoreAsCorrection() {
            scoreboard.startMatch("Mexico", "Canada");
            scoreboard.updateScore("Mexico", "Canada", 2, 1);

            Match corrected = scoreboard.updateScore("Mexico", "Canada", 1, 1);

            assertThat(corrected).hasToString("Mexico 1 – Canada 1");
        }

        @Test
        void keepsStartOrder() {
            Match started = scoreboard.startMatch("Mexico", "Canada");

            Match updated = scoreboard.updateScore("Mexico", "Canada", 1, 0);

            assertThat(updated.startOrder()).isEqualTo(started.startOrder());
        }

        @Test
        void rejectsNegativeScoreAndKeepsPreviousScore() {
            scoreboard.startMatch("Mexico", "Canada");
            scoreboard.updateScore("Mexico", "Canada", 1, 0);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> scoreboard.updateScore("Mexico", "Canada", -1, 0));
            assertThat(summaryLines()).containsExactly("Mexico 1 – Canada 0");
        }

        @Test
        void rejectsMatchNotInProgress() {
            assertThatThrownBy(() -> scoreboard.updateScore("Mexico", "Canada", 1, 0))
                    .isInstanceOfSatisfying(MatchNotFoundException.class, e -> {
                        assertThat(e.getHomeTeam()).isEqualTo("Mexico");
                        assertThat(e.getAwayTeam()).isEqualTo("Canada");
                    });
        }

        @Test
        void rejectsTeamsInWrongOrder() {
            scoreboard.startMatch("Mexico", "Canada");

            assertThatThrownBy(() -> scoreboard.updateScore("Canada", "Mexico", 1, 0))
                    .isInstanceOf(MatchNotFoundException.class);
        }

        @Test
        void rejectsFinishedMatch() {
            scoreboard.startMatch("Mexico", "Canada");
            scoreboard.finishMatch("Mexico", "Canada");

            assertThatThrownBy(() -> scoreboard.updateScore("Mexico", "Canada", 1, 0))
                    .isInstanceOf(MatchNotFoundException.class);
        }
    }

    @Nested
    class FinishMatch {

        @Test
        void removesMatchAndReturnsFinalScore() {
            scoreboard.startMatch("Mexico", "Canada");
            scoreboard.startMatch("Spain", "Brazil");
            scoreboard.updateScore("Mexico", "Canada", 0, 5);

            Match finished = scoreboard.finishMatch("mexico", "canada");

            assertThat(finished).hasToString("Mexico 0 – Canada 5");
            assertThat(summaryLines()).containsExactly("Spain 0 – Brazil 0");
        }

        @Test
        void rejectsMatchNotInProgress() {
            assertThatThrownBy(() -> scoreboard.finishMatch("Mexico", "Canada"))
                    .isInstanceOf(MatchNotFoundException.class);
        }

        @Test
        void rejectsMatchFinishedTwice() {
            scoreboard.startMatch("Mexico", "Canada");
            scoreboard.finishMatch("Mexico", "Canada");

            assertThatThrownBy(() -> scoreboard.finishMatch("Mexico", "Canada"))
                    .isInstanceOf(MatchNotFoundException.class);
        }
    }

    @Nested
    class GetSummary {

        @Test
        void isEmptyForNewScoreboard() {
            assertThat(scoreboard.getSummary()).isEmpty();
        }

        @Test
        void ordersByTotalScoreDescending() {
            startWithScore("Mexico", "Canada", 1, 0);
            startWithScore("Spain", "Brazil", 3, 0);
            startWithScore("Germany", "France", 2, 0);

            assertThat(summaryLines()).containsExactly(
                    "Spain 3 – Brazil 0",
                    "Germany 2 – France 0",
                    "Mexico 1 – Canada 0");
        }

        @Test
        void ordersTiesByMostRecentlyStartedFirst() {
            scoreboard.startMatch("Mexico", "Canada");
            scoreboard.startMatch("Spain", "Brazil");
            scoreboard.startMatch("Germany", "France");

            assertThat(summaryLines()).containsExactly(
                    "Germany 0 – France 0",
                    "Spain 0 – Brazil 0",
                    "Mexico 0 – Canada 0");
        }

        @Test
        void reflectsScoreCorrections() {
            startWithScore("Mexico", "Canada", 2, 0);
            startWithScore("Spain", "Brazil", 1, 0);

            scoreboard.updateScore("Mexico", "Canada", 0, 0);

            assertThat(summaryLines()).containsExactly("Spain 1 – Brazil 0", "Mexico 0 – Canada 0");
        }

        @Test
        void returnsUnmodifiableSnapshot() {
            scoreboard.startMatch("Mexico", "Canada");

            List<Match> summary = scoreboard.getSummary();
            scoreboard.updateScore("Mexico", "Canada", 1, 0);
            scoreboard.startMatch("Spain", "Brazil");

            assertThat(summary).extracting(Match::toString).containsExactly("Mexico 0 – Canada 0");
            assertThatThrownBy(summary::clear).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class Construction {

        @Test
        void rejectsNullRepository() {
            assertThatNullPointerException().isThrownBy(() -> new Scoreboard(null));
        }

        @Test
        void ordersNewMatchesAfterMatchesAlreadyInRepository() {
            InMemoryMatchRepository repository = new InMemoryMatchRepository();
            repository.save(Match.start("Mexico", "Canada", 41));

            Scoreboard withExistingMatches = new Scoreboard(repository);
            Match started = withExistingMatches.startMatch("Spain", "Brazil");

            assertThat(started.startOrder()).isEqualTo(42);
            assertThat(withExistingMatches.getSummary()).extracting(Match::toString)
                    .containsExactly("Spain 0 – Brazil 0", "Mexico 0 – Canada 0");
        }
    }

    private void startWithScore(String homeTeam, String awayTeam, int homeScore, int awayScore) {
        scoreboard.startMatch(homeTeam, awayTeam);
        scoreboard.updateScore(homeTeam, awayTeam, homeScore, awayScore);
    }

    private List<String> summaryLines() {
        return scoreboard.getSummary().stream().map(Match::toString).toList();
    }
}
