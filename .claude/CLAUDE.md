# Project instructions for Claude

## Git / PR workflow

Follow `CONTRIBUTING.md`. In addition:

- Never commit or push to `main`. All work happens on a feature branch
  (`feat/`, `fix/`, `build/`, `docs/`, `refactor/`) with an open PR against `main`.
- **Always push after making changes.** Once a change is committed, push it to its
  feature branch straight away so the PR is up to date. Never leave commits only
  in the local repository.
- Push with an explicit branch name, e.g. `git push -u origin feat/<name>`.
  Bare `git push`, force pushes and pushes to `main` are not allowed.
- When a change affects what the PR does, update the PR description to match.
  (`gh pr edit` fails with gh 2.45, so use
  `gh api -X PATCH repos/db271/sportradar-scoreboard-lib/pulls/<n>`.)
- Merging is done by the user. Never run `gh pr merge`.
