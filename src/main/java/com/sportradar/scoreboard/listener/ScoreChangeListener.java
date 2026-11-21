package com.sportradar.scoreboard.listener;

import com.sportradar.scoreboard.model.Match;

/**
 * Receives a notification each time the score of a match in progress changes.
 *
 * <p>Register it with {@link com.sportradar.scoreboard.Scoreboard#addListener}. Typical uses are
 * pushing live updates to consumers (websites, apps, odds engines) without polling
 * {@link com.sportradar.scoreboard.Scoreboard#getSummary()}.
 *
 * <h2>Delivery guarantees</h2>
 * <ul>
 *     <li>Called after the new score is stored, on the thread that called
 *     {@code updateScore}, and without holding the scoreboard's lock. A listener may therefore
 *     call back into the scoreboard.</li>
 *     <li>Not called if the update leaves the score unchanged, or if the update fails.</li>
 *     <li>When several threads update scores at the same time, their notifications may arrive in
 *     any order. Use {@code previous} and {@code current} rather than the arrival order to tell
 *     what changed.</li>
 *     <li>An exception thrown by a listener is logged and doesn't affect the update or the other
 *     listeners.</li>
 * </ul>
 */
@FunctionalInterface
public interface ScoreChangeListener {

    /**
     * Called after the score of a match has changed.
     *
     * @param previous the match before the update
     * @param current  the match after the update; same teams and start order, different score
     */
    void onScoreChanged(Match previous, Match current);
}
