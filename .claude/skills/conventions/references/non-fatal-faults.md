# Non-fatal faults

**Rule.** At presenter edges, use one internal `:ui` helper over `catturaNonFatale` to log the operation and its cause
at `WARNING` through a `java.util.logging` logger under `snastro.ui`, then show M1(b)'s generic message. Never discard a
failure with `catturaNonFatale { … }.getOrNull()` without logging through `onFailure`; cancellation and fatal errors
still propagate.

**Why.** CR-7 in `.mismagent/code-rules.md`: a swallowed cause leaves no trace to debug a user's report. One helper keeps
the log format and the generic message in one place, within CR-19b's frozen catch-all counts. Release review R0-1 found
the cause dropped in three runners; refactor slices 12 and 14 land the helper (cite it here once it exists).
Decided: codex (delegated by the user), 2026-10-08.

**Shown by** (today the defect, to be fixed by slices 12 and 14): `ui/src/main/kotlin/snastro/ui/progetti/CorsaSingola.kt`,
`ui/src/main/kotlin/snastro/ui/EliminazioneProgettoAperto.kt`, `ui/src/main/kotlin/snastro/ui/ShellPresenter.kt`.
