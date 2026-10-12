VERDICT: PASS
SHA: 8d31628fe1805c0e7943544cc5dc969aa188c78c
SCORES: simple=4 naming=4 modularity=4 duplication=4 concision=4 errors=5 tests=5

# Review 43-preferenze-illeggibili-registrate, cycle 2 (slice, refactor)

Base `e66d64f1`. Reviewed: `git diff e66d64f1..HEAD`, with focus on the rework `9fcaa061..8d31628f`.
Evidence: `mm check --base e66d64f1`: green (only the pre-existing ADR "no Evidence" warnings); `mm gate`: green
(test + lint, so `:ui` and `:avvio` compile and pass against the shared helper).

| dimension | score | evidence |
|---|---|---|
| simple design | 4 | `PreferenzeAppFile.kt:28-33`: one catch, one log line, same fallback; `RegistriDi.kt:11-28` one function |
| naming | 4 | `registriDi(logger, blocco)` reads as "records of"; matches the existing `registriLogUi`; logger per-class (`PreferenzeAppFile.kt:17`) |
| modularity | 4 | helper in `:supporto-test` (`supporto-test/src/main/kotlin/snastro/supporto/test/RegistriDi.kt`), JDK only, no snastro dependency (ARCHITECTURE.md:46); both users already have `testImplementation(project(":supporto-test"))` (`avvio/build.gradle.kts:84`, `ui/build.gradle.kts:32`) |
| duplication | 4 | B1 of cycle 1 resolved: the copy in `PreferenzeAppFileTest.kt` is gone, `LogUi.kt:11` `registriLogUi` delegates in one line. The other hand-rolled spy (`SessioneProgettoImplChiudiTest.kt:281-290`) is outside the diff: design note added |
| concision | 4 | `SwallowedException` suppression removed; no unused imports left (`Handler`, `LogRecord`, `assertTrue` dropped from the test) |
| errors | 5 | CR-7: WARNING with the `IOException` as thrown (`PreferenzeAppFile.kt:31`); missing file stays silent (`isRegularFile`, `:29`); loggers held strongly by file-level vals (`PreferenzeAppFile.kt:17`, `PreferenzeAppFileTest.kt:18`) |
| tests | 5 | Decision met: malformed UTF-8 makes the read really throw (`PreferenzeAppFileTest.kt:41`), fallback and record both checked (`:43-48`); the cycle-1 advice taken, the full message is asserted (`assertEquals("leggi le preferenze da $file", ...)`, `:47`); missing-file test asserts no record (`:30-33`); existing tests unchanged |

Conventions: the first shared test helper is proposed in `.mismagent/conventions-proposals.md:2` ("create
test-helpers"), and the proposal matches the diff (helper path, `registriLogUi` delegating, the caller holding the
logger strongly).

## Blocking
None.

## Advice (not blocking)
- `RegistriDi.kt:11` is `public inline` with an anonymous `Handler` inside: each call site gets its own class. Harmless
  for tests; a non-inline function would do the same unless a caller needs a non-local return or suspend in `blocco`.
