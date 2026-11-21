# How AI was used

## Tools

- **Claude Code** (CLI, model Claude Opus 5.5), working directly in the repository and the
  terminal. It read the task PDF, wrote code, tests and docs, ran Maven, and used `git` and
  `gh` to create branches, commits and pull requests.
- **Guardrails.** Claude Code permission rules (kept local in `.claude/settings.local.json`,
  not committed) let it push only to feature branches. Pushes to `main`, force pushes,
  deleting branches and merging PRs were blocked, so every merge was done by me. When I
  wanted to rewrite `main`'s history, Claude prepared it and I ran the force push myself.

## Summary

I used AI as a pair programmer that proposes, and I decided. The work went through these
stages:

1. **Requirements analysis.** Claude listed the open questions in the task (15 of them) with
   options and a recommendation for each, before any design.
2. **Design.** I accepted most recommendations and changed some: storage behind an interface,
   classes grouped into packages, and a read/write lock after I questioned `synchronized`.
   The result was written to [`design/DESIGN.md`](design/DESIGN.md) and reviewed as its own PR.
3. **Workflow.** Claude set up the PR template and `CONTRIBUTING.md`. I cut it down: no CI,
   no Dependabot, no issue templates, and no committed Claude settings. I added a rule that
   every PR is reviewed right after it's opened.
4. **Implementation.** Delivered as pull requests: skeleton and core (#3), then the extra
   feature on its own (#4), then this documentation (#5). I changed the plan along the way,
   merging the planned model, repository and core PRs into one, and moving README/AI.md to
   the end.
5. **Review.** From #3 on, Claude reviewed the full diff right after opening each PR, as a
   reviewer would, posted the findings on the PR and pushed fixes. #1 and #2 went through
   before that rule was in place. I reviewed and merged every PR.
6. **History.** I merged the first PRs with merge commits instead of squashing. Claude
   rebuilt `main` as one commit per PR, with exactly the same files, and I force-pushed it.

### What I checked, and what the reviews caught

AI output wasn't taken on trust. These are the concrete checks and findings:

- **Every PR was built and tested locally** with `mvn verify` before pushing (96 tests at the
  end). A build that printed a stack trace was re-run and inspected; the trace turned out to
  be the expected log from the failing-listener test.
- **The test that listeners run after the lock is released was itself tested.** The
  notification was temporarily moved inside the lock; the test failed after 5 seconds, and
  the correct code was restored.
- **The README usage example was compiled and run** against the built classes.
- **The PR reviews found real issues, and each was fixed before merging:**
  - #3: four Maven plugins (clean, resources, install, deploy) weren't pinned, even though
    the PR description said all were. They fell back to Maven 3.8's defaults from years ago.
  - #3: the `MatchRepository.findAll()` contract required a snapshot for a reason that
    wasn't true.
  - #3/#2: the design document had drifted from the code, so it was updated.
  - #4: `removeListener` didn't mention that a notification already in progress can still
    arrive after the listener is removed.
- **A correction from me:** I asked whether `synchronized` was the right choice. The answer
  was balanced: a read/write lock helps only `getSummary`, but it's worth it once storage can
  be swapped. Both points are in the README's trade-offs.

## Prompt history

My prompts, verbatim (including typos), with a short note on what came out of each.

| # | Prompt | Outcome |
|---|--------|---------|
| 1 | read the task in docs/ODDS and Data - JAVA Coding Task.pdf. what's unclear or open in the requirements? give options for each and what you'd pick, don't design yet | A list of 15 open questions with options and a recommendation for each. |
| 2 | ok, propose a design, keep it simple. Choose your pick up options, but for 12 answer use b. | A first design with all recommendations accepted, except storage, which goes behind a `MatchRepository` interface. |
| 3 | Group classes to packages, like exception in one package, entities another ... . Is the synchronize good option as it always block all methods, maybe read/write loging would be better? We will be pushing all changes to https://github.com/db271/sportradar-scoreboard-lib repo, could you prepare PR workflow with templates. | Packages `model`, `repository`, `exception` and `listener`. Switched to a `ReentrantReadWriteLock` after looking at the trade-offs. Added a PR template, `CONTRIBUTING.md`, CI and issue templates. |
| 4 | as main push just .gitignore, for PR workflow, create PR | `main` holds only `.gitignore`; the workflow files went to PR #1. |
| 5 | add docs/ODDS and Data - JAVA Coding Task.pdf to gitignore. Remove this files .github/workflows/ci.yml and .github/dependabot.yml. Also add to CONTRIBUTING.md after creating a PR, make a review of it. | The task PDF is git-ignored, CI and Dependabot are removed, and every PR gets a review right after it is opened. |
| 6 | yes, push and update the PR description | PR #1 updated. |
| 7 | remove issue template | Issue templates removed. |
| 8 | add .claude and let claude make pushes with feature branches. Always push to PR after doing any changes, do not leave them locally. | Claude Code permission rules: push to feature branches only, never `main`, no force pushes, no merging. |
| 9 | setting.json move to settings.local.json and add it to .gitignore. Do we need calude.md as it is repeat of CONTRIBUTING.md i think we can remove it. | The settings are kept local (git-ignored), and the project `CLAUDE.md` is removed. |
| 10 | save the design to design/DESIGN.md and create PR for it. how would you split the rest into PR's? extra feature needs its own | `design/DESIGN.md` (PR #2) and a plan of 8 PRs, with the extra feature in its own PR. |
| 11 | ok, go ahead with next PR | PR #3: Maven skeleton, `README.md` and `AI.md`. Its review found lifecycle plugins without pinned versions, which were then pinned. |
| 12 | AI and readme put in the last PR. and add in this 4,5,6 step | `README.md` and `AI.md` moved to the final PR. The model, exceptions, repository and `Scoreboard` core were added to PR #3 (82 tests). Its review found the `findAll()` contract too strict, which was relaxed, and `DESIGN.md` updated to match. |
| 13 | merged, go ahead with next PR | PR #4: score-change listeners (the extra feature), with 14 new tests. A test was checked by breaking the code on purpose to confirm it catches the bug. The review found that a late notification after `removeListener` wasn't documented; this is now in the Javadoc. |
| 14 | could you rewrite main branch that we will have grouped commits as PR's | I stopped the first attempt (a read-only status check) to make the request more precise. |
| 15 | could you rewrite main branch that we will have grouped commits as PR's, like they were squased then merged | `main` rebuilt locally as `.gitignore` plus one commit per PR, each checked to have exactly the same files as the original merge. The force push is blocked for Claude, so I ran it myself with `--force-with-lease`. |
| 16 | go ahead with next PR | PR #5 (this one): final `README.md` and `AI.md`. |

## Artifacts that guided the implementation

- [`design/DESIGN.md`](design/DESIGN.md): decisions on the open questions (options,
  choice, reasoning), components, the concurrency approach, the extra feature, the testing
  strategy and the delivery plan. It was kept in sync with the code as the work went on.
- [`CONTRIBUTING.md`](CONTRIBUTING.md) and
  [`.github/pull_request_template.md`](.github/pull_request_template.md): the workflow
  every change went through.
- The pull request descriptions and review comments on GitHub (#1–#5). Each records the
  decisions, the trade-offs and what the review found for that step.
- The task description PDF (not committed, because it's Sportradar's document).
