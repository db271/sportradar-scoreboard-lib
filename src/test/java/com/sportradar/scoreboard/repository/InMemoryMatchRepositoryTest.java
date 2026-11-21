package com.sportradar.scoreboard.repository;

import com.sportradar.scoreboard.model.Match;
import com.sportradar.scoreboard.model.MatchId;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryMatchRepositoryTest {

    private final InMemoryMatchRepository repository = new InMemoryMatchRepository();

    @Test
    void isEmptyInitially() {
        assertThat(repository.findAll()).isEmpty();
        assertThat(repository.find(MatchId.of("Mexico", "Canada"))).isEmpty();
    }

    @Test
    void findsSavedMatchById() {
        Match match = Match.start("Mexico", "Canada", 1);

        repository.save(match);

        assertThat(repository.find(MatchId.of("mexico", "CANADA"))).contains(match);
    }

    @Test
    void saveReplacesMatchWithSameId() {
        Match match = Match.start("Mexico", "Canada", 1);
        repository.save(match);

        Match updated = match.withScore(0, 5);
        repository.save(updated);

        assertThat(repository.find(match.id())).contains(updated);
        assertThat(repository.findAll()).containsExactly(updated);
    }

    @Test
    void removeDeletesMatch() {
        Match match = Match.start("Mexico", "Canada", 1);
        repository.save(match);

        repository.remove(match.id());

        assertThat(repository.find(match.id())).isEmpty();
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void removeOfUnknownIdDoesNothing() {
        Match match = Match.start("Mexico", "Canada", 1);
        repository.save(match);

        repository.remove(MatchId.of("Spain", "Brazil"));

        assertThat(repository.findAll()).containsExactly(match);
    }

    @Test
    void findAllReturnsUnmodifiableSnapshot() {
        Match mexicoCanada = Match.start("Mexico", "Canada", 1);
        repository.save(mexicoCanada);

        Collection<Match> snapshot = repository.findAll();
        repository.save(Match.start("Spain", "Brazil", 2));

        assertThat(snapshot).containsExactly(mexicoCanada);
        assertThatThrownBy(snapshot::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> repository.find(null));
        assertThatNullPointerException().isThrownBy(() -> repository.save(null));
        assertThatNullPointerException().isThrownBy(() -> repository.remove(null));
    }
}
