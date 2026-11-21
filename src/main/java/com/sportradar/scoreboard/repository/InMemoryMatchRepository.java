package com.sportradar.scoreboard.repository;

import com.sportradar.scoreboard.model.Match;
import com.sportradar.scoreboard.model.MatchId;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * {@link MatchRepository} backed by a {@link HashMap}.
 *
 * <p>Not thread-safe on its own. It relies on the locking contract described in
 * {@link MatchRepository}: concurrent reads of a {@code HashMap} are safe as long as no write
 * runs at the same time.
 */
public final class InMemoryMatchRepository implements MatchRepository {

    private final Map<MatchId, Match> matches = new HashMap<>();

    @Override
    public Optional<Match> find(MatchId id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(matches.get(id));
    }

    @Override
    public void save(Match match) {
        Objects.requireNonNull(match, "match");
        matches.put(match.id(), match);
    }

    @Override
    public void remove(MatchId id) {
        Objects.requireNonNull(id, "id");
        matches.remove(id);
    }

    @Override
    public Collection<Match> findAll() {
        return List.copyOf(matches.values());
    }
}
