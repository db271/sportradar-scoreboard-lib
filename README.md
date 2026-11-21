# Live Football World Cup Scoreboard

A small, thread-safe Java library that keeps track of live football matches and their
scores. It's a solution to the Sportradar *Data & Odds Platform* coding exercise.

The full design, including every option considered, is in
[`design/DESIGN.md`](design/DESIGN.md). This README summarises it.

## Requirements

- Java 21 or newer
- Maven 3.8 or newer

The library has no runtime dependencies. JUnit 5 and AssertJ are used for tests only.

## Build and test

```bash
mvn verify     # compile, run the 96 tests, build target/scoreboard-lib-1.0.0-SNAPSHOT.jar
mvn install    # also install it to the local Maven repository
```

One test makes a listener fail on purpose, so the output contains a WARNING with a stack
trace saying `listener failure (expected by test)`. That's expected.

## Usage

```java
Scoreboard scoreboard = new Scoreboard();

scoreboard.startMatch("Mexico", "Canada");                 // starts at 0–0
scoreboard.updateScore("Mexico", "Canada", 0, 5);          // absolute score, not an increment

scoreboard.startMatch("Spain", "Brazil");
scoreboard.updateScore("Spain", "Brazil", 10, 2);

for (Match match : scoreboard.getSummary()) {
    System.out.println(match);                             // "Spain 10 – Brazil 2", then "Mexico 0 – Canada 5"
}

Match result = scoreboard.finishMatch("Mexico", "Canada"); // removed; returns the final score

// Extra feature: be told about score changes instead of polling the summary
scoreboard.addListener((previous, current) ->
        System.out.println(previous + "  ->  " + current));
```

### API

| Method | Behaviour |
|--------|-----------|
| `Match startMatch(home, away)` | Adds a match at 0–0. Throws `TeamAlreadyPlayingException` if either team is already playing. |
| `Match updateScore(home, away, homeScore, awayScore)` | Sets the absolute score; lower values are accepted as corrections. Throws `MatchNotFoundException` if the match isn't in progress. |
| `Match finishMatch(home, away)` | Removes the match and returns it with its final score. Throws `MatchNotFoundException` if the match isn't in progress. |
| `List<Match> getSummary()` | Matches in progress, highest total score first; ties go to the most recently started match. Returns an unmodifiable copy. |
| `addListener` / `removeListener` | Register or unregister a `ScoreChangeListener` (the extra feature). |

Invalid arguments (`null` or blank team names, a team playing itself, negative scores) throw
`IllegalArgumentException`. Errors caused by the board's state extend `ScoreboardException`.

### Packages

```
com.sportradar.scoreboard              Scoreboard               the public entry point
com.sportradar.scoreboard.model        Match, MatchId           immutable value types
com.sportradar.scoreboard.repository   MatchRepository,         storage you can swap out
                                       InMemoryMatchRepository
com.sportradar.scoreboard.exception    ScoreboardException, MatchNotFoundException,
                                       TeamAlreadyPlayingException
com.sportradar.scoreboard.listener     ScoreChangeListener      the extra feature
```

## Assumptions

The task says its requirements leave some questions open on purpose. These are the answers
I chose:

1. **A new match starts at 0–0.** A match that's already underway can be started and then
   updated straight away.
2. **`updateScore` takes absolute scores**, not increments or goal events. Repeating the same
   call gives the same result, and this matches the score snapshots in the task's example.
3. **A score may go down.** That's treated as a correction (for example a goal disallowed by
   VAR). Negative scores are rejected, and there is no upper limit.
4. **A match is identified by its home and away team names.** A team can only play one match
   at a time, so the pair is unique among live matches.
5. **A team can't be in two live matches.** Starting Canada–Mexico while Mexico–Canada is
   live, or Mexico–Spain while Mexico is playing, is rejected. So is a team playing itself.
6. **Team names are compared ignoring case and surrounding whitespace.** `" spain "` and
   `"Spain"` are the same team. The original spelling (trimmed) is kept for display.
7. **Home and away order matters.** `updateScore("Canada", "Mexico", …)` doesn't find the
   match Mexico–Canada.
8. **Finishing a match removes it.** The summary only lists matches in progress, and the
   library doesn't keep a history.
9. **Operations on a match that isn't in progress throw** `MatchNotFoundException` rather
   than doing nothing, so mistakes in the calling code don't go unnoticed.
10. **"Most recently started" means start order, not time.** A counter that goes up with each
    start gives a strict order even when two matches start in the same millisecond.
11. **The summary is a copy** of the board at the time of the call, as immutable `Match`
    records. It never changes afterwards, and callers can't modify it.
12. **The board lives in memory** in a single process, behind a `MatchRepository` interface.
13. **Callers may use the scoreboard from several threads.** "Multiple simultaneous matches"
    plus live feeds make that the realistic case.

## Reasoning

- **Keep it simple.** The task asks for a "simple library", so there's one entry point
  (`Scoreboard`) and a few immutable records, with no frameworks and no runtime dependencies.
  Each package holds one kind of thing.
- **Immutable values.** `Match` is a record, and an update replaces it with a new instance.
  This means a summary can be handed out without defensive copying, a listener gets reliable
  `previous` and `current` values, and nothing changes behind the caller's back.
- **Each check-then-act step happens atomically.** `startMatch` (check that no team is
  playing, then insert) and `updateScore` / `finishMatch` (look up, then replace or remove)
  each run entirely under the write lock. Two threads therefore can't both start a match for
  the same team, which a concurrency test checks.
- **Read/write lock rather than `synchronized`.** `getSummary` is the only read-only
  operation, and it takes the shared read lock, so several summaries can be read at once.
  This matters most once storage is swapped for something slower than a `HashMap`. The
  summary is copied while the lock is held and sorted after it's released, so the lock is
  held as briefly as possible.
- **Validate early, fail loudly.** Team names are checked before the lock is taken, and
  scores before anything is stored. Exceptions say what went wrong (`Team 'Mexico' is already playing in a match in progress:
  Mexico 0 – Canada 5`), and a failed operation never changes the board.
- **Tests are behaviour specifications.** `ScoreboardTest` is grouped by operation, and the
  task's example scenario is a test of its own. 96 tests cover validation, ordering,
  corrections, concurrency and the listener's guarantees.

## Trade-offs

| Decision | Benefit | Cost |
|----------|---------|------|
| Team names as the match key | Simple API that matches the task's wording | Typos create a `MatchNotFoundException` rather than being caught at compile time; an id or handle returned by `startMatch` would be safer |
| Checking whether a team is playing by scanning all live matches | No second index to keep in sync | O(n) per start; fine for a World Cup (at most a few dozen live matches) |
| `ReentrantReadWriteLock` | Summary reads run in parallel | More code than `synchronized`; only one operation benefits; repositories must allow concurrent reads |
| A lock that only protects one process | Simple and correct in memory | A repository shared between processes would need its own transactions or locking |
| Not chosen: lock-free reads (an immutable sorted snapshot kept in a `volatile` field) | Readers would never wait | Every write would rebuild the snapshot; overkill at this scale, but the next step if reads became the bottleneck |
| Removing finished matches | Simple; the summary only needs live matches | No history of results (a natural follow-up feature) |
| Allowing scores to go down | Corrections are possible | A wrong call can't be told apart from a correction |
| `startOrder` counter instead of timestamps | Strict ordering that tests can rely on | No "started at" time is available to callers |
| `-Xlint:all` with warnings as errors | Problems are fixed when they appear | The build occasionally fails on something harmless |

## Extra feature: score-change listeners

```java
scoreboard.addListener((previous, current) -> push(current));
```

A `ScoreChangeListener` is notified with the match before and after each score change.

**Why this feature.** A sports-data platform pushes live updates to odds engines, websites
and apps. Telling consumers when a score changes fits that better than having them poll
`getSummary()`. It's also the feature that tests the concurrency design most: when listeners
are called relative to the lock, and how a failing listener is kept from affecting others.

**What callers can rely on**
- Listeners are called **after the new score is stored and the lock is released**, on the
  thread that called `updateScore`. A slow listener, or one that calls back into the
  scoreboard, can't block or deadlock it. A test checks this and fails if the notification
  is moved back inside the lock.
- Listeners are called only for **real changes**. Setting the same score, a failed update,
  and starting or finishing a match don't notify anyone.
- A listener that **throws** is logged through `System.Logger`. The update still stands, and
  the other listeners are still called.
- Adding the same listener twice has no effect. A listener can remove itself while being
  notified.

**Limitations**
- When two threads update scores at the same moment, their notifications can arrive in
  either order. Consumers should rely on `previous` and `current`, not on arrival order.
  Keeping the order would mean calling listeners while holding the lock, which is exactly
  what the design avoids.
- A notification already in progress on another thread can still reach a listener just
  after `removeListener` returns.

The feature was introduced in a single commit: *Add score-change listeners (extra feature)
(#4)*.

## How this was built

The work was delivered as small pull requests, each reviewed right after it was opened and
squashed into one commit on `main`. See [CONTRIBUTING.md](CONTRIBUTING.md) for the workflow
and [AI.md](AI.md) for how AI tools were used, including the full prompt history.
