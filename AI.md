# How AI was used

## Summary

_To be completed when the implementation is finished._

The solution is developed with **Claude Code** (model: Claude Opus 5.5), working in the
repository and terminal. So far it has been used to:

- read the task PDF and list the open questions in the requirements, with options and a
  recommendation for each;
- propose a design, then refine it based on my feedback (packages, read/write lock instead
  of `synchronized`, a pluggable repository);
- set up the pull request workflow: PR template, `CONTRIBUTING.md`, `.gitignore`;
- write the design document and plan how to split the work into pull requests;
- create branches, commits and pull requests through `git` and `gh`.

I made the decisions. Claude proposed options and I accepted, changed or rejected them
(see the prompt history below).

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
| 11 | ok, go ahead with next PR | This PR: Maven skeleton, `README.md` and `AI.md`. |

## Artifacts that guided the implementation

- [`design/DESIGN.md`](design/DESIGN.md): decisions on the open questions, the components,
  the concurrency approach, the extra feature, the testing strategy and the PR plan.
- [`CONTRIBUTING.md`](CONTRIBUTING.md) and the PR template: the workflow every change goes
  through.
- The task description PDF (not committed).
