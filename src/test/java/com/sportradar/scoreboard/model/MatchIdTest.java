package com.sportradar.scoreboard.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class MatchIdTest {

    @Test
    void normalisesTeamNames() {
        MatchId id = MatchId.of("  Spain ", "BRAZIL");

        assertThat(id.homeTeamKey()).isEqualTo("spain");
        assertThat(id.awayTeamKey()).isEqualTo("brazil");
    }

    @Test
    void idsDifferingOnlyInCaseAndWhitespaceAreEqual() {
        assertThat(MatchId.of("Spain", "Brazil"))
                .isEqualTo(MatchId.of(" spain", "BRAZIL "))
                .hasSameHashCodeAs(MatchId.of(" spain", "BRAZIL "));
    }

    @Test
    void homeAndAwayOrderMatters() {
        assertThat(MatchId.of("Spain", "Brazil")).isNotEqualTo(MatchId.of("Brazil", "Spain"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsInvalidHomeTeam(String homeTeam) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MatchId.of(homeTeam, "Brazil"))
                .withMessageContaining("homeTeam");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsInvalidAwayTeam(String awayTeam) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MatchId.of("Spain", awayTeam))
                .withMessageContaining("awayTeam");
    }

    @Test
    void rejectsTeamPlayingAgainstItselfIgnoringCase() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MatchId.of("Spain", " SPAIN "))
                .withMessageContaining("cannot play against itself");
    }

    @Test
    void sharesTeamWithDetectsAnyCommonTeam() {
        MatchId mexicoCanada = MatchId.of("Mexico", "Canada");

        assertThat(mexicoCanada.sharesTeamWith(MatchId.of("Mexico", "Spain"))).isTrue();
        assertThat(mexicoCanada.sharesTeamWith(MatchId.of("Spain", "Mexico"))).isTrue();
        assertThat(mexicoCanada.sharesTeamWith(MatchId.of("Canada", "Spain"))).isTrue();
        assertThat(mexicoCanada.sharesTeamWith(MatchId.of("Spain", "Canada"))).isTrue();
        assertThat(mexicoCanada.sharesTeamWith(MatchId.of("Canada", "Mexico"))).isTrue();
        assertThat(mexicoCanada.sharesTeamWith(MatchId.of("Spain", "Brazil"))).isFalse();
    }

    @Test
    void involvesChecksBothTeams() {
        MatchId id = MatchId.of("Mexico", "Canada");

        assertThat(id.involves("mexico")).isTrue();
        assertThat(id.involves("canada")).isTrue();
        assertThat(id.involves("spain")).isFalse();
    }
}
