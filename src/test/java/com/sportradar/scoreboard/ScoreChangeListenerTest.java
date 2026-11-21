package com.sportradar.scoreboard;

import com.sportradar.scoreboard.exception.MatchNotFoundException;
import com.sportradar.scoreboard.listener.ScoreChangeListener;
import com.sportradar.scoreboard.model.Match;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class ScoreChangeListenerTest {

    private final Scoreboard scoreboard = new Scoreboard();
    private final RecordingListener recorder = new RecordingListener();

    @Test
    void notifiesWithPreviousAndCurrentMatch() {
        scoreboard.addListener(recorder);
        Match started = scoreboard.startMatch("Mexico", "Canada");

        Match updated = scoreboard.updateScore("Mexico", "Canada", 0, 1);

        assertThat(recorder.changes).containsExactly(new Change(started, updated));
    }

    @Test
    void notifiesEveryChangeInOrderForSingleThread() {
        scoreboard.addListener(recorder);
        scoreboard.startMatch("Mexico", "Canada");

        scoreboard.updateScore("Mexico", "Canada", 1, 0);
        scoreboard.updateScore("Mexico", "Canada", 2, 0);
        scoreboard.updateScore("Mexico", "Canada", 1, 0); // correction

        assertThat(recorder.lines()).containsExactly(
                "Mexico 0 – Canada 0 -> Mexico 1 – Canada 0",
                "Mexico 1 – Canada 0 -> Mexico 2 – Canada 0",
                "Mexico 2 – Canada 0 -> Mexico 1 – Canada 0");
    }

    @Test
    void doesNotNotifyWhenScoreIsUnchanged() {
        scoreboard.addListener(recorder);
        scoreboard.startMatch("Mexico", "Canada");
        scoreboard.updateScore("Mexico", "Canada", 1, 0);

        scoreboard.updateScore("Mexico", "Canada", 1, 0);

        assertThat(recorder.changes).hasSize(1);
    }

    @Test
    void doesNotNotifyOnStartOrFinish() {
        scoreboard.addListener(recorder);

        scoreboard.startMatch("Mexico", "Canada");
        scoreboard.finishMatch("Mexico", "Canada");

        assertThat(recorder.changes).isEmpty();
    }

    @Test
    void doesNotNotifyWhenUpdateFails() {
        scoreboard.addListener(recorder);
        scoreboard.startMatch("Mexico", "Canada");

        assertThatThrownBy(() -> scoreboard.updateScore("Spain", "Brazil", 1, 0))
                .isInstanceOf(MatchNotFoundException.class);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> scoreboard.updateScore("Mexico", "Canada", -1, 0));

        assertThat(recorder.changes).isEmpty();
    }

    @Test
    void notifiesAllListenersInRegistrationOrder() {
        List<String> calls = new ArrayList<>();
        scoreboard.addListener((previous, current) -> calls.add("first"));
        scoreboard.addListener((previous, current) -> calls.add("second"));
        scoreboard.startMatch("Mexico", "Canada");

        scoreboard.updateScore("Mexico", "Canada", 1, 0);

        assertThat(calls).containsExactly("first", "second");
    }

    @Test
    void failingListenerDoesNotAffectUpdateOrOtherListeners() {
        scoreboard.addListener((previous, current) -> {
            throw new IllegalStateException("listener failure (expected by test)");
        });
        scoreboard.addListener(recorder);
        scoreboard.startMatch("Mexico", "Canada");

        Match updated = scoreboard.updateScore("Mexico", "Canada", 1, 0);

        assertThat(updated).hasToString("Mexico 1 – Canada 0");
        assertThat(scoreboard.getSummary()).containsExactly(updated);
        assertThat(recorder.changes).hasSize(1);
    }

    @Test
    void addingSameListenerTwiceNotifiesItOnce() {
        scoreboard.addListener(recorder);
        scoreboard.addListener(recorder);
        scoreboard.startMatch("Mexico", "Canada");

        scoreboard.updateScore("Mexico", "Canada", 1, 0);

        assertThat(recorder.changes).hasSize(1);
    }

    @Test
    void removedListenerIsNoLongerNotified() {
        scoreboard.addListener(recorder);
        scoreboard.startMatch("Mexico", "Canada");
        scoreboard.updateScore("Mexico", "Canada", 1, 0);

        scoreboard.removeListener(recorder);
        scoreboard.updateScore("Mexico", "Canada", 2, 0);

        assertThat(recorder.changes).hasSize(1);
    }

    @Test
    void removingUnknownListenerDoesNothing() {
        scoreboard.removeListener(recorder);

        scoreboard.startMatch("Mexico", "Canada");
        scoreboard.updateScore("Mexico", "Canada", 1, 0);

        assertThat(recorder.changes).isEmpty();
    }

    @Test
    void listenerCanRemoveItselfDuringNotification() {
        AtomicInteger calls = new AtomicInteger();
        ScoreChangeListener once = new ScoreChangeListener() {
            @Override
            public void onScoreChanged(Match previous, Match current) {
                calls.incrementAndGet();
                scoreboard.removeListener(this);
            }
        };
        scoreboard.addListener(once);
        scoreboard.startMatch("Mexico", "Canada");

        scoreboard.updateScore("Mexico", "Canada", 1, 0);
        scoreboard.updateScore("Mexico", "Canada", 2, 0);

        assertThat(calls).hasValue(1);
    }

    @Test
    void rejectsNullListener() {
        assertThatNullPointerException().isThrownBy(() -> scoreboard.addListener(null));
        assertThatNullPointerException().isThrownBy(() -> scoreboard.removeListener(null));
    }

    @Test
    void notifiesAfterReleasingTheLock() {
        // Another thread needs the write lock while the listener runs. If the listener were
        // called while the lock is held, startMatch would block and the future would time out.
        List<Match> startedFromOtherThread = new CopyOnWriteArrayList<>();
        scoreboard.addListener((previous, current) -> startedFromOtherThread.add(
                CompletableFuture.supplyAsync(() -> scoreboard.startMatch("Spain", "Brazil"))
                        .orTimeout(5, TimeUnit.SECONDS)
                        .join()));
        scoreboard.startMatch("Mexico", "Canada");

        scoreboard.updateScore("Mexico", "Canada", 1, 0);

        assertThat(startedFromOtherThread).extracting(Match::toString).containsExactly("Spain 0 – Brazil 0");
    }

    @Test
    void everyConcurrentChangeIsNotifiedExactlyOnce() throws Exception {
        int threads = 8;
        int updatesPerThread = 100;
        AtomicInteger notifications = new AtomicInteger();
        scoreboard.addListener((previous, current) -> notifications.incrementAndGet());
        for (int t = 0; t < threads; t++) {
            scoreboard.startMatch("Home " + t, "Away " + t);
        }

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch startSignal = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int thread = t;
                futures.add(executor.submit(() -> {
                    startSignal.await();
                    for (int goal = 1; goal <= updatesPerThread; goal++) {
                        scoreboard.updateScore("Home " + thread, "Away " + thread, goal, 0);
                    }
                    return null;
                }));
            }
            startSignal.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(notifications).hasValue(threads * updatesPerThread);
    }

    private record Change(Match previous, Match current) {
    }

    private static final class RecordingListener implements ScoreChangeListener {

        private final List<Change> changes = new CopyOnWriteArrayList<>();

        @Override
        public void onScoreChanged(Match previous, Match current) {
            changes.add(new Change(previous, current));
        }

        List<String> lines() {
            return changes.stream().map(c -> c.previous() + " -> " + c.current()).toList();
        }
    }
}
