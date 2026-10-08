# Non-fatal faults

**Rule.** At presenter edges, use one internal `:ui` helper over `catturaNonFatale` to log the operation and its cause
at `WARNING` through a `java.util.logging` logger under `snastro.ui`, then show M1(b)'s generic message. Never discard a
failure with `catturaNonFatale { … }.getOrNull()` without logging through `onFailure`; cancellation and fatal errors
still propagate.

**Committing blocks** (added 2026-10-08, ADR 0041, decided: codex on delegation; lands with slices 18–19): ADR 0041
supersedes the blanket helper rule for committing blocks (`comandoConfermato`, imports): they use
`eccezioneRegistrata(operazione) { … }`, which catches `Exception` only, rethrows cancellation and lets every `Error`
propagate (D-0065). Other presenter edges use `nonFataleRegistrato`. Both log operation and cause at WARNING through one
shared logging function; S2 per-row commands use the single runner on `StatoS2`.

**Why.** CR-7 in `.mismagent/code-rules.md`: a swallowed cause leaves no trace to debug a user's report. One helper keeps
the log format and the generic message in one place, within CR-19b's frozen catch-all counts. Release review R0-1 found
the cause dropped in three runners; the helper is `nonFataleRegistrato(operazione) { … }` (slice 12); slice 14 moves
the shell's own runner onto it.
Decided: codex (delegated by the user), 2026-10-08.

**Shown by:** the helper `ui/src/main/kotlin/snastro/ui/GuastoNonFatale.kt`; its users
`ui/src/main/kotlin/snastro/ui/progetti/CorsaSingola.kt`, `ui/src/main/kotlin/snastro/ui/EliminazioneProgettoAperto.kt`,
`ui/src/main/kotlin/snastro/ui/ShellPresenter.kt` (`corri`), and a read at
`ui/src/main/kotlin/snastro/ui/progetti/ProgettiPresenter.kt` (`caricaElenco`);
the test spy on the `snastro.ui` logger `ui/src/test/kotlin/snastro/ui/LogUi.kt` (`registriLogUi`, `causatoDa`).
