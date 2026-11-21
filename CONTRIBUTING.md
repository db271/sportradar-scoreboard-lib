# Contributing

## Workflow

1. Never commit directly to `main`; every change goes through a pull request.
2. Create a short-lived branch from an up-to-date `main`:

   | Prefix      | Use for                              | Example                     |
   |-------------|--------------------------------------|-----------------------------|
   | `feat/`     | New functionality                    | `feat/score-change-listener`|
   | `fix/`      | Bug fixes                            | `fix/summary-tie-break`     |
   | `refactor/` | Internal changes, no behaviour change| `refactor/match-id`         |
   | `build/`    | Maven, CI, tooling                   | `build/maven-skeleton`      |
   | `docs/`     | README / AI.md / Javadoc only        | `docs/trade-offs`           |

3. Keep each PR focused on one logical change, and keep it small enough to review in one sitting.
4. Run `mvn verify` locally before pushing.
5. Open a PR against `main` and fill in the template.
6. CI (`.github/workflows/ci.yml`) must be green before merging.
7. Merge with **Squash and merge**, so each PR becomes one descriptive commit on `main`.
   This keeps the history readable and guarantees that the extra feature lands as a
   distinct commit.
8. Delete the branch after merging.

## Commit messages

- Imperative summary line, about 50 characters (`Add score-change listener`).
- A body explaining *what* and *why* when it isn't obvious from the diff.

## Code standards

- Java 21, no runtime dependencies.
- Every behaviour change comes with tests (JUnit 5 + AssertJ).
- Public API gets Javadoc that states its exceptions and thread-safety guarantees.
- Update `README.md` when assumptions, behaviour or trade-offs change, and `AI.md`
  with the prompts used for the change.
