package com.sportradar.scoreboard;

import com.sportradar.scoreboard.exception.TeamAlreadyPlayingException;
import com.sportradar.scoreboard.model.Match;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests for thread safety. They can't prove the absence of races, but they fail reliably
 * if the check-then-act steps in {@link Scoreboard} stop being atomic.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class ScoreboardConcurrencyTest {

    private static final int THREADS = 16;

    private final Scoreboard scoreboard = new Scoreboard();

    @RepeatedTest(20)
    void onlyOneOfManyConflictingStartsSucceeds() throws Exception {
        List<Callable<Boolean>> attempts = IntStream.range(0, THREADS)
                .<Callable<Boolean>>mapToObj(i -> () -> {
                    try {
                        scoreboard.startMatch("Mexico", "Opponent " + i);
                        return true;
                    } catch (TeamAlreadyPlayingException e) {
                        return false;
                    }
                })
                .toList();

        List<Boolean> results = runConcurrently(attempts);

        assertThat(results).containsOnlyOnce(true);
        assertThat(scoreboard.getSummary()).hasSize(1);
    }

    @Test
    void concurrentOperationsLeaveConsistentBoard() throws Exception {
        int matchesPerThread = 50;
        List<Callable<Void>> workers = IntStream.range(0, THREADS)
                .<Callable<Void>>mapToObj(thread -> () -> {
                    for (int m = 0; m < matchesPerThread; m++) {
                        String home = "Home " + thread + "-" + m;
                        String away = "Away " + thread + "-" + m;
                        scoreboard.startMatch(home, away);
                        scoreboard.updateScore(home, away, m % 5, thread % 3);
                        if (m % 2 == 0) {
                            scoreboard.finishMatch(home, away);
                        }
                        scoreboard.getSummary();
                    }
                    return null;
                })
                .toList();

        runConcurrently(workers);

        List<Match> summary = scoreboard.getSummary();
        assertThat(summary).hasSize(THREADS * matchesPerThread / 2);
        assertThat(summary).extracting(Match::startOrder).doesNotHaveDuplicates();
        assertThat(summary).isSortedAccordingTo((a, b) -> {
            int byScore = Long.compare(b.totalScore(), a.totalScore());
            return byScore != 0 ? byScore : Long.compare(b.startOrder(), a.startOrder());
        });
    }

    /** Starts all tasks at the same moment and returns their results in order. */
    private static <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch startSignal = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(executor.submit(() -> {
                    startSignal.await();
                    return task.call();
                }));
            }
            startSignal.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
