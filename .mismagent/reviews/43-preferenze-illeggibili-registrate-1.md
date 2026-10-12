VERDICT: REWORK
SHA: 9fcaa061644cabcbb6616a52d3881c99a507f5cc
SCORES: simple=4 naming=4 modularity=4 duplication=3 concision=4 errors=5 tests=4

# Review 43-preferenze-illeggibili-registrate, cycle 1 (slice, refactor)

Base `e66d64f1`. Diff: `avvio/src/main/kotlin/snastro/avvio/PreferenzeAppFile.kt` (+9/-6),
`avvio/src/test/kotlin/snastro/avvio/PreferenzeAppFileTest.kt` (+50), progress.md, slice file.
Evidence: `mm gate` green; `mm check --base e66d64f1` green (only the pre-existing ADR "no Evidence" warnings);
`./gradlew :avvio:test --tests snastro.avvio.PreferenzeAppFileTest`: 6 tests, 0 failures.

| dimension | score | evidence |
|---|---|---|
| simple design | 4 | `PreferenzeAppFile.kt:28-33`: one catch, one log line, the same fallback |
| naming | 4 | logger `snastro.avvio.PreferenzeAppFile` (`:17`), the same per-class form as `SessioneProgettoImpl.kt:467`; operation text `leggi le preferenze da $file` (`:31`) as the slice asks |
| modularity | 4 | stays in `:avvio`; `PreferenzeApp` contract unchanged (never throws), as ADR 0041 / the ARCHITECTURE.md amendment (lines 288-290) ask |
| duplication | **3** | `PreferenzeAppFileTest.kt:74-93` is a verbatim copy of `registriLogUi` (`ui/src/test/kotlin/snastro/ui/LogUi.kt:11-27`) with another logger; see B1 |
| concision | 4 | nothing unused; the `SwallowedException` suppression is gone (old `:26-28`) |
| errors | 5 | CR-7: the cause is logged at WARNING with the exception as `thrown` (`:31`); a missing file stays silent (`isRegularFile`, `:29`); the logger is held strongly by a file-level val, so the record does not go to a GC'd logger (cf. `CartellaLogApp.kt:45-54`) |
| tests | 4 | `PreferenzeAppFileTest.kt:28-48`: one behavior each, named as the behavior; the Decision is met: malformed UTF-8 (`:40`) makes `newBufferedReader` really throw `MalformedInputException` on every OS and user, and the test checks both the fallback (`:42`) and the record (level, file, `IOException` as thrown, `:44-47`); the missing-file test asserts no record (`:29-32`); the existing tests are unchanged |

## Blocking

**B1. Duplication (table: "shared once in the right place"; the same publish-to-list handler 3+ times).**
`avvio/src/test/kotlin/snastro/avvio/PreferenzeAppFileTest.kt:74-93` (`registriDi`) is a line-for-line copy of
`ui/src/test/kotlin/snastro/ui/LogUi.kt:11-27` (`registriLogUi`). Only the logger differs. The same list-collecting
spy `Handler` also appears in `avvio/src/test/kotlin/snastro/avvio/progetto/SessioneProgettoImplChiudiTest.kt:281-290`.
This diff adds a third copy instead of sharing the helper. `:supporto-test` is the place for test-only helpers
(ARCHITECTURE.md:46, no snastro dependency, and `java.util.logging` is JDK). Both `:avvio` and `:ui` already depend on
it (`avvio/build.gradle.kts:84`, `ui/build.gradle.kts:32`).
**Fix:** move one `registriDi(logger: Logger, blocco: () -> Unit): List<LogRecord>` into
`supporto-test/src/main/kotlin/snastro/supporto/test/`. Make `registriLogUi` delegate to it (`registriDi(loggerUi, blocco)`),
and have `PreferenzeAppFileTest` call it with a strongly held `Logger.getLogger(PreferenzeAppFile::class.java.name)`.
No assertion changes. This shares a test helper for the first time, so propose it in
`.mismagent/conventions-proposals.md`, or record it in the `:supporto-test` row of ARCHITECTURE.md as the architect
prefers.

## Advice (not blocking)
- `PreferenzeAppFileTest.kt:46`: the test checks that the file is in the message but not the operation
  (`leggi le preferenze`). `assertTrue(registro.message.startsWith("leggi le preferenze da "))` would cover the
  contract in full.
- The user-visible message stays in design-notes.md:22-26 as the Decision says. That is correct, and the rule bars
  adding it here.
