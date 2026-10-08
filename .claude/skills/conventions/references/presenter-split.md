# Presenter split

**Rule.** Split a presenter by user flow when it gains multiple reasons to change, using `internal` holders over its
existing screen state. Preserve its constructor, `stato` and `azioni`; bind extracted actions directly to holder
functions, without forwarding members or lambdas hiding `TooManyFunctions`. Preserve each screen's concurrency
contract: S1 holders share one `internal` `CorsaSingola` over one `MutableStateFlow`, owning the synchronous M3 guard,
the M1(b) generic message for non-fatal faults, and the `inCorso` reset in `finally`; never duplicate that runner.

**Why.** Thin UI and one screen contract (RC-2 in `.mismagent/code-rules.md`): the screen keeps one state and one
`azioni` while each flow gets its own small home. A lambda in `azioni` or a forwarding member only hides the size.
Other screens keep their own concurrency (e.g. the audio of `RegistrazionePresenter`). Not yet uniform practice:
some presenters still grow with an explicit suppression; new growth splits instead.
Decided: codex (delegated by the user), 2026-10-08.

**Shown by:** `ui/src/main/kotlin/snastro/ui/progetti/CorsaSingola.kt`, `ui/src/main/kotlin/snastro/ui/progetti/FlussoEliminazione.kt`,
`ui/src/main/kotlin/snastro/ui/progetti/ProgettiPresenter.kt`; an earlier split with shared state:
`ui/src/main/kotlin/snastro/ui/registrazione/RegistrazionePresenter.kt` with `ui/src/main/kotlin/snastro/ui/registrazione/StatoVoci.kt`.
