package com.sportradar.scoreboard.repository;

import com.sportradar.scoreboard.model.Match;
import com.sportradar.scoreboard.model.MatchId;

import java.util.Collection;
import java.util.Optional;

/**
 * Storage for the matches in progress, keyed by {@link MatchId}.
 *
 * <h2>Thread safety</h2>
 * The scoreboard calls a repository only while holding its read/write lock:
 * <ul>
 *     <li>{@link #find} and {@link #findAll} may be called by several threads at the same time,
 *     but never while a write is running;</li>
 *     <li>{@link #save} and {@link #remove} are never called at the same time as any other
 *     method.</li>
 * </ul>
 * An implementation therefore needs no locking of its own, as long as concurrent reads are safe.
 */
public interface MatchRepository {

    /**
     * Returns the match with the given id, or an empty {@code Optional} if there is none.
     */
    Optional<Match> find(MatchId id);

    /**
     * Stores the match under {@link Match#id()}, replacing any match already stored under that id.
     */
    void save(Match match);

    /**
     * Removes the match with the given id. Does nothing if there is none.
     */
    void remove(MatchId id);

    /**
     * Returns all stored matches in no particular order.
     *
     * <p>The returned collection is a snapshot: it must not change when the repository changes
     * later, because the scoreboard sorts it after releasing its lock.
     */
    Collection<Match> findAll();
}
