package com.sportradar.scoreboard;

import com.sportradar.scoreboard.exception.MatchNotFoundException;
import com.sportradar.scoreboard.exception.TeamAlreadyPlayingException;
import com.sportradar.scoreboard.listener.ScoreChangeListener;
import com.sportradar.scoreboard.model.Match;
import com.sportradar.scoreboard.model.MatchId;
import com.sportradar.scoreboard.repository.InMemoryMatchRepository;
import com.sportradar.scoreboard.repository.MatchRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A live scoreboard for football matches in progress.
 *
 * <p>Matches are identified by their home and away team names. Names are compared ignoring
 * case and surrounding whitespace, and a team can only play in one match at a time.
 *
 * <h2>Thread safety</h2>
 * This class is thread-safe. Operations that change the board take an exclusive write lock;
 * {@link #getSummary()} takes a shared read lock, so summaries can be read in parallel.
 * {@link ScoreChangeListener}s are called after the lock is released.
 */
public final class Scoreboard {

    private static final System.Logger LOGGER = System.getLogger(Scoreboard.class.getName());

    /** Highest total score first; on a tie, the most recently started match first. */
    private static final Comparator<Match> SUMMARY_ORDER = Comparator
            .comparingLong(Match::totalScore).reversed()
            .thenComparing(Comparator.comparingLong(Match::startOrder).reversed());

    private final MatchRepository repository;
    private final Lock readLock;
    private final Lock writeLock;

    /** Copy-on-write, so listeners can be added or removed while notifications are sent. */
    private final CopyOnWriteArrayList<ScoreChangeListener> listeners = new CopyOnWriteArrayList<>();

    /** Start order given to the most recently started match. Guarded by {@link #writeLock}. */
    private long lastStartOrder;

    /**
     * Creates an empty scoreboard that keeps matches in memory.
     */
    public Scoreboard() {
        this(new InMemoryMatchRepository());
    }

    /**
     * Creates a scoreboard that stores matches in the given repository.
     *
     * <p>The repository may already contain matches. New matches are then ordered after them.
     *
     * @throws NullPointerException if {@code repository} is {@code null}
     */
    public Scoreboard(MatchRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
        ReadWriteLock lock = new ReentrantReadWriteLock();
        this.readLock = lock.readLock();
        this.writeLock = lock.writeLock();
        this.lastStartOrder = repository.findAll().stream()
                .mapToLong(Match::startOrder)
                .max()
                .orElse(0);
    }

    /**
     * Starts a new match with a score of 0–0 and adds it to the scoreboard.
     *
     * @return the started match
     * @throws IllegalArgumentException    if a team name is {@code null} or blank, or both names
     *                                     refer to the same team
     * @throws TeamAlreadyPlayingException if either team is already playing in a match in progress
     */
    public Match startMatch(String homeTeam, String awayTeam) {
        MatchId id = MatchId.of(homeTeam, awayTeam);
        writeLock.lock();
        try {
            for (Match live : repository.findAll()) {
                MatchId liveId = live.id();
                if (liveId.sharesTeamWith(id)) {
                    String team = liveId.involves(id.homeTeamKey()) ? homeTeam : awayTeam;
                    throw new TeamAlreadyPlayingException(team.strip(), live.toString());
                }
            }
            Match match = Match.start(homeTeam, awayTeam, lastStartOrder + 1);
            repository.save(match);
            lastStartOrder = match.startOrder();
            return match;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Sets the score of a match in progress.
     *
     * <p>The scores are absolute values, not increments. A score lower than the current one is
     * accepted as a correction, for example after a disallowed goal.
     *
     * <p>If the score changes, registered {@link ScoreChangeListener}s are notified after the new
     * score is stored and the lock is released.
     *
     * @return the match with its new score
     * @throws IllegalArgumentException if a team name is {@code null} or blank, both names refer to
     *                                  the same team, or a score is negative
     * @throws MatchNotFoundException   if no match between these teams is in progress
     */
    public Match updateScore(String homeTeam, String awayTeam, int homeScore, int awayScore) {
        MatchId id = MatchId.of(homeTeam, awayTeam);
        Match previous;
        Match updated;
        writeLock.lock();
        try {
            previous = repository.find(id)
                    .orElseThrow(() -> new MatchNotFoundException(homeTeam, awayTeam));
            updated = previous.withScore(homeScore, awayScore);
            repository.save(updated);
        } finally {
            writeLock.unlock();
        }
        if (!updated.equals(previous)) {
            notifyListeners(previous, updated);
        }
        return updated;
    }

    /**
     * Registers a listener to be notified of score changes. Adding a listener that is already
     * registered has no effect.
     *
     * @throws NullPointerException if {@code listener} is {@code null}
     */
    public void addListener(ScoreChangeListener listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.addIfAbsent(listener);
    }

    /**
     * Unregisters a listener. Does nothing if it isn't registered.
     *
     * <p>Listeners are notified outside the lock, so a notification already in progress on another
     * thread may still reach the listener shortly after this method returns.
     *
     * @throws NullPointerException if {@code listener} is {@code null}
     */
    public void removeListener(ScoreChangeListener listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.remove(listener);
    }

    /**
     * Finishes a match in progress and removes it from the scoreboard.
     *
     * @return the match with its final score
     * @throws IllegalArgumentException if a team name is {@code null} or blank, or both names refer
     *                                  to the same team
     * @throws MatchNotFoundException   if no match between these teams is in progress
     */
    public Match finishMatch(String homeTeam, String awayTeam) {
        MatchId id = MatchId.of(homeTeam, awayTeam);
        writeLock.lock();
        try {
            Match finished = repository.find(id)
                    .orElseThrow(() -> new MatchNotFoundException(homeTeam, awayTeam));
            repository.remove(id);
            return finished;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Returns the matches in progress, ordered by total score (highest first). Matches with the
     * same total score are ordered with the most recently started match first.
     *
     * @return an unmodifiable list, taken at the time of the call; later changes to the scoreboard
     *         don't affect it
     */
    public List<Match> getSummary() {
        List<Match> matches;
        readLock.lock();
        try {
            matches = List.copyOf(repository.findAll());
        } finally {
            readLock.unlock();
        }
        // Sorting a private copy doesn't need the lock, so it's done after releasing it.
        return matches.stream().sorted(SUMMARY_ORDER).toList();
    }

    /**
     * Calls every listener. A failing listener is logged and doesn't stop the others: the update
     * is already stored, so throwing to the caller of {@code updateScore} would be misleading.
     */
    private void notifyListeners(Match previous, Match current) {
        for (ScoreChangeListener listener : listeners) {
            try {
                listener.onScoreChanged(previous, current);
            } catch (RuntimeException e) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Score change listener " + listener + " failed for " + current, e);
            }
        }
    }
}
