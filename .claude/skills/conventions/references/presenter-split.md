# Presenter split

**Rule.** Split a presenter by user flow when it gains multiple reasons to change, using `internal` holders over its
existing screen state. Preserve its `stato` and `azioni` (the constructor's parameter list may change, see
**Collaborators of an extracted flow**); bind extracted actions directly to holder
functions, without forwarding members or lambdas hiding `TooManyFunctions`. Preserve each screen's concurrency
contract: S1 holders share one `internal` `CorsaSingola` over one `MutableStateFlow`, owning the synchronous M3 guard,
the M1(b) generic message for non-fatal faults, and the `inCorso` reset in `finally`; never duplicate that runner.

**Collaborators of an extracted flow** (2026-10-09, decided: codex on delegation; replaces "preserve its
constructor"): group the flow's collaborators in one public data class per `internal` holder, built by `:avvio` and
required by the presenter's constructor in place of those collaborators. Every collaborator and bundle is a required,
non-null parameter, with no defaults or fallbacks: omitted wiring fails to compile (ADR 0030 §1). Preserve `stato`,
`azioni`, the direct bindings, the shared state and the screen's concurrency contract. Not: `internal` bundles — `:avvio`
could not build them (`ui/src/main/kotlin/snastro/ui/registrazioni/RegistrazioniPresenter.kt`, `avvio/src/main/kotlin/snastro/avvio/Presenter.kt`).

**The shell** (added 2026-10-08, decided: codex on delegation): the shell's extracted holder uses the shell's existing
`MutableStateFlow` and receives its M3 guard as `occupata: () -> Boolean`; the presenter includes the holder's in-flight
flag in that guard, keeping the shell's own concurrency contract without `CorsaSingola`; the holder handles non-fatal
faults through `catturaNonFatale` and resets its flag in `finally` (`ui/src/main/kotlin/snastro/ui/EliminazioneProgettoAperto.kt`,
`ui/src/main/kotlin/snastro/ui/ShellPresenter.kt`).

**Shell operations** (added 2026-10-08, decided: codex on delegation; lands with slice 14): shell `apri`/`crea`/`chiudi`
share one private single-flight runner owning the synchronous M3 guard, `Caricamento`, fault handling through the
non-fatal-faults helper, and cleanup in `finally`; never a try/catch runner per operation. The open-project delete
holder keeps its own guard and flag, coordinated through `occupata()`.

**S2 · Registrazioni** (added 2026-10-08, decided: codex on delegation): S2 holders (`FlussoImporta`, `FlussoEliminaRiga`, and since slice 20 `FlussoIncontro` — Incontro commands and the
chevron's `espansi` set, read by the refresh through `espanso(id)`, no copy)
share one `internal StatoS2` over `_stato`, owning `aggiornaDati`/`aggiornaRiga`, `rigaLibera`, `azzeraAvviso` and
`ricarica` supplied as `::carica`; preserve S2's per-row concurrency and import guard, with no screen-wide single-flight
runner; initialize `s2` and the holders before `init`; bind shared-state-only actions directly
(`chiudiAvviso = s2::azzeraAvviso`). Slice 16 redistributed the two existing CR-19b catch counts without raising the
total (not a general permission to transfer frozen allowances); keep cancellation/Error propagation and `finally` cleanup.
Their unlogged causes remain non-fatal-faults/CR-7 debt, not an approved exception
(`ui/src/main/kotlin/snastro/ui/registrazioni/StatoS2.kt`).

**S2 row runner** (2026-10-09, decided: codex on delegation; built by slice 33): `StatoS2` in
`ui/src/main/kotlin/snastro/ui/registrazioni/StatoS2.kt` receives `io`; `comandoDiRiga(id, operazione, comando,
suEsito, suGuasto)` runs `eccezioneRegistrata(operazione) { suEsito(withContext(io) { comandoConfermato(operazione,
comando) }) }`, like `suRiga` in `ui/src/main/kotlin/snastro/ui/parlanti/ParlantiPresenter.kt`. Callers pass the plain
port call and name the operation once; `suEsito` runs on the presenter dispatcher. `FlussoImporta`/`FlussoIncontro`
keep their own blocks (not row commands). Preserve `suGuasto`, row/import guards, logging, cancellation/Error
propagation and `finally` cleanup.

**Why.** Thin UI and one screen contract (RC-2 in `.mismagent/code-rules.md`): the screen keeps one state and one
`azioni` while each flow gets its own small home. A lambda in `azioni` or a forwarding member only hides the size.
Other screens keep their own concurrency (e.g. the audio of `RegistrazionePresenter`). Not yet uniform practice:
some presenters still grow with an explicit suppression; new growth splits instead.
Decided: codex (delegated by the user), 2026-10-08.

**Shown by:** `ui/src/main/kotlin/snastro/ui/progetti/CorsaSingola.kt`, `ui/src/main/kotlin/snastro/ui/progetti/FlussoEliminazione.kt`,
`ui/src/main/kotlin/snastro/ui/progetti/ProgettiPresenter.kt`; an earlier split with shared state:
`ui/src/main/kotlin/snastro/ui/registrazione/RegistrazionePresenter.kt` with `ui/src/main/kotlin/snastro/ui/registrazione/StatoVoci.kt`.
