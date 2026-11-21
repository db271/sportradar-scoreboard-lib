package com.sportradar.scoreboard.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class MatchTest {

    @Test
    void startCreatesMatchAtZeroZero() {
        Match match = Match.start("Mexico", "Canada", 1);

        assertThat(match.homeTeam()).isEqualTo("Mexico");
        assertThat(match.awayTeam()).isEqualTo("Canada");
        assertThat(match.homeScore()).isZero();
        assertThat(match.awayScore()).isZero();
        assertThat(match.startOrder()).isEqualTo(1);
    }

    @Test
    void trimsTeamNamesButKeepsTheirCase() {
        Match match = Match.start("  Mexico ", "Canada\t", 1);

        assertThat(match.homeTeam()).isEqualTo("Mexico");
        assertThat(match.awayTeam()).isEqualTo("Canada");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void rejectsInvalidTeamNames(String teamName) {
        assertThatIllegalArgumentException().isThrownBy(() -> Match.start(teamName, "Canada", 1));
        assertThatIllegalArgumentException().isThrownBy(() -> Match.start("Mexico", teamName, 1));
    }

    @Test
    void rejectsTeamPlayingAgainstItself() {
        assertThatIllegalArgumentException().isThrownBy(() -> Match.start("Mexico", "mexico", 1));
    }

    @Test
    void rejectsNegativeScores() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Match("Mexico", "Canada", -1, 0, 1))
                .withMessageContaining("homeScore");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Match("Mexico", "Canada", 0, -1, 1))
                .withMessageContaining("awayScore");
    }

    @Test
    void withScoreReturnsUpdatedCopyAndLeavesOriginalUnchanged() {
        Match original = Match.start("Mexico", "Canada", 7);

        Match updated = original.withScore(0, 5);

        assertThat(updated).isEqualTo(new Match("Mexico", "Canada", 0, 5, 7));
        assertThat(original.homeScore()).isZero();
        assertThat(original.awayScore()).isZero();
    }

    @Test
    void withScoreRejectsNegativeScores() {
        Match match = Match.start("Mexico", "Canada", 1);

        assertThatIllegalArgumentException().isThrownBy(() -> match.withScore(-1, 0));
    }

    @Test
    void totalScoreAddsBothScores() {
        assertThat(new Match("Spain", "Brazil", 10, 2, 1).totalScore()).isEqualTo(12);
    }

    @Test
    void totalScoreDoesNotOverflow() {
        Match match = new Match("Spain", "Brazil", Integer.MAX_VALUE, Integer.MAX_VALUE, 1);

        assertThat(match.totalScore()).isEqualTo(2L * Integer.MAX_VALUE);
    }

    @Test
    void idIsBasedOnNormalisedTeamNames() {
        assertThat(Match.start("Mexico", "Canada", 1).id()).isEqualTo(MatchId.of("MEXICO", "canada"));
    }

    @Test
    void toStringUsesScoreboardFormat() {
        assertThat(new Match("Mexico", "Canada", 0, 5, 1)).hasToString("Mexico 0 – Canada 5");
    }
}
