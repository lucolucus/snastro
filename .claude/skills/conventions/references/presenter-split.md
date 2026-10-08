# Presenter split

**Rule.** Split a presenter by user flow when it gains multiple reasons to change, using `internal` holders over its
existing screen state. Preserve its constructor, `stato` and `azioni`; bind extracted actions directly to holder
functions, without forwarding members or lambdas hiding `TooManyFunctions`. Preserve each screen's concurrency
contract: S1 holders share one `internal` `CorsaSingola` over one `MutableStateFlow`, owning the synchronous M3 guard,
the M1(b) generic message for non-fatal faults, and the `inCorso` reset in `finally`; never duplicate that runner.

**The shell** (added 2026-10-08, decided: codex on delegation): the shell's extracted holder uses the shell's existing
`MutableStateFlow` and receives its M3 guard as `occupata: () -> Boolean`; the presenter includes the holder's in-flight
flag in that guard, keeping the shell's own concurrency contract without `CorsaSingola`; the holder handles non-fatal
faults through `catturaNonFatale` and resets its flag in `finally` (`ui/src/main/kotlin/snastro/ui/EliminazioneProgettoAperto.kt`,
`ui/src/main/kotlin/snastro/ui/ShellPresenter.kt`).

**Shell operations** (added 2026-10-08, decided: codex on delegation; lands with slice 14): shell `apri`/`crea`/`chiudi`
share one private single-flight runner owning the synchronous M3 guard, `Caricamento`, fault handling through the
non-fatal-faults helper, and cleanup in `finally`; never a try/catch runner per operation. The open-project delete
holder keeps its own guard and flag, coordinated through `occupata()`.

**Why.** Thin UI and one screen contract (RC-2 in `.mismagent/code-rules.md`): the screen keeps one state and one
`azioni` while each flow gets its own small home. A lambda in `azioni` or a forwarding member only hides the size.
Other screens keep their own concurrency (e.g. the audio of `RegistrazionePresenter`). Not yet uniform practice:
some presenters still grow with an explicit suppression; new growth splits instead.
Decided: codex (delegated by the user), 2026-10-08.

**Shown by:** `ui/src/main/kotlin/snastro/ui/progetti/CorsaSingola.kt`, `ui/src/main/kotlin/snastro/ui/progetti/FlussoEliminazione.kt`,
`ui/src/main/kotlin/snastro/ui/progetti/ProgettiPresenter.kt`; an earlier split with shared state:
`ui/src/main/kotlin/snastro/ui/registrazione/RegistrazionePresenter.kt` with `ui/src/main/kotlin/snastro/ui/registrazione/StatoVoci.kt`.
