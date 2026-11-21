# Live Football World Cup Scoreboard: Design

This document covers the design of the scoreboard library: how the open points in the
requirements are resolved, the structure of the code, and the order the work is delivered
in. The requirements say they deliberately leave questions open, so every decision here is
an explicit assumption. The final `README.md` will summarise them.

## 1. Requirements: decisions on the open points

| #  | Question | Options considered | Decision | Reasoning |
|----|----------|--------------------|----------|-----------|
| 1  | Score when a match starts | always 0–0 / caller provides a score | **Always 0–0** | Simplest, and matches how a live feed works. A match that's already underway can be started and then updated. |
| 2  | What "update score" means | absolute pair / increment one team / goal event | **Absolute pair** `(homeScore, awayScore)` | Repeating the same call gives the same result, corrections are possible, and the example in the task is given as score snapshots. |
| 3  | Can a score go down? | reject / allow | **Allow** (as a correction). Negative scores are rejected. No upper limit. | Real feeds correct scores, for example after a goal is disallowed by VAR. Rejecting decreases would make corrections impossible. |
| 4  | How callers identify a match | team names / an ID returned by start / an ID supplied by the caller | **Home and away team names** | A team can only play one live match at a time, so the pair is a natural key. The task describes matches by team names. |
| 5  | Invalid or conflicting starts | reject all / reject exact duplicates only | **Reject**: null or blank names, a team playing itself, and a team already playing in any live match | The last rule also rules out "Canada–Mexico" being started while "Mexico–Canada" is live. |
| 6  | Team name normalisation | exact match / trim and ignore case | **Trim and ignore case for matching; keep the original spelling for display** | Stops "Spain" and "spain " counting as different teams, at almost no cost. |
| 7  | What "finish" does | remove from the board / keep as a finished match | **Remove** | The summary lists only matches in progress, and the task asks for a simple library. |
| 8  | Operation on an unknown or finished match | throw / do nothing / return a boolean or `Optional` | **Throw `MatchNotFoundException`** | Fail loudly. Doing nothing would hide bugs in the calling code. |
| 9  | Which match started most recently | wall-clock time / a counter | **A counter (`startOrder`) that increases with each start** | Two starts in the same millisecond would tie on time and make the order unpredictable. A counter gives a strict order that tests can rely on. |
| 10 | What the summary returns | immutable records / formatted strings / a live view | **An unmodifiable `List<Match>` copied at the time of the call** | Callers get structured data. Formatting is available through `Match.toString()`. |
| 11 | Thread safety | not thread-safe / one lock / concurrent collections | **Thread-safe, using a `ReentrantReadWriteLock`** | Live feeds update from several threads. See §4. |
| 12 | Storage | in memory / in memory behind an interface | **In memory, behind a `MatchRepository` interface** | The scoreboard logic doesn't depend on how matches are stored. |
| 13 | The extra feature | score-change listeners / history of finished matches / look up a team's match / undo | **Score-change listeners** | The most relevant choice for a sports-data company that pushes live updates. See §5. |
| 14 | Tooling | Java 17 / Java 21 | **Java 21, Maven, JUnit 5 and AssertJ for tests, no runtime dependencies** | Records, and a current LTS release. |

## 2. Package structure

```
com.sportradar.scoreboard              Scoreboard                     (public API entry point)
com.sportradar.scoreboard.model        Match, MatchId
com.sportradar.scoreboard.repository   MatchRepository, InMemoryMatchRepository
com.sportradar.scoreboard.exception    ScoreboardException, MatchNotFoundException,
                                       TeamAlreadyPlayingException
com.sportradar.scoreboard.listener     ScoreChangeListener            (extra feature)
```

Trade-off: once the code is split across packages, a few members have to be public that
could otherwise be package-private (`Match.withScore`, `MatchId.of`). This is harmless
because both types are immutable.

## 3. Components

### `model.Match` (record)

```java
record Match(String homeTeam, String awayTeam, int homeScore, int awayScore, long startOrder)
```

- The compact constructor rejects null or blank team names and negative scores.
- `int totalScore()` returns `homeScore + awayScore`.
- `Match withScore(int homeScore, int awayScore)` returns a new `Match` with the new score.
- `MatchId id()` returns the match's key.
- `toString()` returns `"Mexico 0 – Canada 5"`.

### `model.MatchId` (record)

- `MatchId.of(home, away)` trims and lowercases each name (using `Locale.ROOT`). It throws
  `IllegalArgumentException` for null or blank names, or when both names are the same team.
- It is the key used for storage and lookups. `Match` keeps the original spelling for display.

### `repository.MatchRepository` (interface)

```java
Optional<Match> find(MatchId id);
void save(Match match);        // insert or replace
void remove(MatchId id);
Collection<Match> findAll();
```

The contract: `Scoreboard` makes every call while holding its lock. Reads (`find`, `findAll`)
may run at the same time as each other, but never at the same time as a write. Because of
that, implementations need no locking of their own. `InMemoryMatchRepository` wraps a
`HashMap`.

### `exception`

- `ScoreboardException extends RuntimeException`: a common base class, so callers can catch
  every scoreboard error with one `catch`.
- `MatchNotFoundException`: update or finish was called for a match that isn't live.
- `TeamAlreadyPlayingException`: a team in the new match is already playing in a live match.
- Bad arguments (null or blank names, a team playing itself, negative scores) throw the
  standard `IllegalArgumentException`.

### `Scoreboard`

```java
Scoreboard()                                   // uses InMemoryMatchRepository
Scoreboard(MatchRepository repository)
Match startMatch(String homeTeam, String awayTeam)
Match updateScore(String homeTeam, String awayTeam, int homeScore, int awayScore)
void finishMatch(String homeTeam, String awayTeam)
List<Match> getSummary()
```

| Operation | Lock | Behaviour |
|-----------|------|-----------|
| `startMatch` | write | Throws `TeamAlreadyPlayingException` if either team is playing in any live match (checked by scanning all live matches, O(n)). Otherwise saves the match at 0–0 with `startOrder = ++sequence`. |
| `updateScore` | write | Looks up the match by `MatchId` (or throws `MatchNotFoundException`), then saves `withScore(...)`. Returns the updated match. |
| `finishMatch` | write | Looks up the match (or throws `MatchNotFoundException`), then removes it. |
| `getSummary` | read | Sorts by `totalScore` highest first, then by `startOrder` highest first, and returns `List.copyOf(...)`. |

Checking the expected order from the task: Argentina 3–1 and Germany 2–2 both total 4.
Argentina was started later, so it comes first.

## 4. Concurrency

A `ReentrantReadWriteLock` (non-fair) protects the scoreboard and its repository.

- `startMatch` (check whether a team is playing, then insert) and `updateScore` (read the
  match, then replace it) must each run as one indivisible step, so they take the **write**
  lock for the whole operation. `finishMatch` takes the write lock as well.
- `getSummary` is the only read-only operation. It takes the **read** lock, so several
  summary calls can run at the same time.
- **Why not `synchronized`?** It would also make summary reads wait for each other. Since
  storage can be swapped (§1, #12), a slower repository would make summary reads the
  expensive part, and those are the calls a read/write lock lets run in parallel.
- **Cost:** a little more code (`lock`/`unlock` in `try`/`finally`), and repositories have
  to allow concurrent reads (the contract in §3).
- **Not chosen: lock-free reads.** Every write would rebuild an immutable sorted snapshot
  and keep it in a `volatile` field, so readers would never wait. That's overkill at this
  scale, but it's the next step if reads became the bottleneck.
- The lock only protects one process. A repository shared between processes would need its
  own transactions or locking.

## 5. Extra feature: score-change listeners

```java
@FunctionalInterface
public interface ScoreChangeListener {
    void onScoreChanged(Match previous, Match current);
}

void addListener(ScoreChangeListener listener);
void removeListener(ScoreChangeListener listener);
```

- **Why this feature:** a sports-data company pushes live updates to its consumers (odds
  engines, websites, apps). Calling the consumer when a score changes means it doesn't have
  to keep polling `getSummary()`.
- Listeners are kept in a `CopyOnWriteArrayList`, so they can be added or removed while
  notifications are being sent, even by a listener removing itself. Adding a listener that is
  already registered has no effect (`addIfAbsent`).
- Listeners are called only when the score actually changes. An update that sets the same
  score, or one that fails, doesn't notify anyone. Starting and finishing a match don't
  notify either: the feature is about score changes.
- `updateScore` changes the state while holding the write lock, and **calls the listeners
  only after releasing it**. That way a slow listener, or one that calls back into the
  scoreboard, can't block or deadlock it.
- If a listener throws a `RuntimeException`, it is logged through `System.Logger` (no logging
  dependency) and the remaining listeners are still called. `Error`s are not caught. The update has already been committed at that point, so
  passing the exception on to the caller of `updateScore` would mislead them.
- Known limitation, documented in the `ScoreChangeListener` Javadoc: when two threads update
  scores at the same moment, their notifications can arrive in either order. Consumers should
  rely on `previous` and `current`, not on arrival order. Keeping the order would mean calling
  listeners while holding the lock, which is the problem described above.

## 6. Testing strategy

- Unit tests for each class (JUnit 5 + AssertJ), covering failure and edge cases: blank
  names, a team playing itself, negative scores, unknown matches, a team already playing.
- The example scenario from the task as an end-to-end ordering test.
- Ordering tests that cover ties broken by start order and score corrections.
- A concurrency smoke test: many threads start, update and finish matches, and the test
  checks that the board ends up consistent.
- Listener tests: called with the previous and current match; not called when an operation
  fails; a listener that throws doesn't stop the other listeners.

## 7. Delivery plan (pull requests)

Each PR is merged with squash and merge, so it becomes one commit on `main`.

| PR | Branch | Contents |
|----|--------|----------|
| 1 | `build/pr-workflow` | PR template, `CONTRIBUTING.md`, `.gitignore` |
| 2 | `docs/design` | This document |
| 3 | `build/maven-skeleton` | `pom.xml` (Java 21, JUnit 5, AssertJ), `README.md` and `AI.md` with placeholder sections, a prompt log so far |
| 4 | `feat/domain-model` | `model` and `exception` packages, with tests |
| 5 | `feat/match-repository` | `MatchRepository` and `InMemoryMatchRepository`, with tests |
| 6 | `feat/scoreboard-core` | `Scoreboard` with the four required operations, the read/write lock, the example scenario test and a concurrency test |
| 7 | `feat/score-change-listener` | **The extra feature only.** This is the distinct commit the task requires. |
| 8 | `docs/readme-ai` | Final `README.md` (assumptions, reasoning, trade-offs, the extra feature) and `AI.md` (how AI was used, prompt history, artifacts) |
