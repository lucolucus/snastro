# Presenter split

**Rule.** When a presenter gains several reasons to change, split it by user flow into `internal` holders over its
screen state. Keep `stato` and `azioni`, and bind actions directly to holder functions: no forwarding members, no
lambdas hiding `TooManyFunctions`. Keep each screen's concurrency contract and never duplicate its runner. A holder carries at most a reasoned
`LongParameterList`, never `TooManyFunctions`: past detekt's 11 it is split by flow, and helpers that touch no holder
state are file-private functions (2026-10-11, decided by the user).

**Collaborators** (2026-10-09, decided: codex on delegation): one public data class per holder, built by `:avvio` and
required by the constructor in place of those collaborators. Every parameter is non-null with no default, so omitted
wiring fails to compile (ADR 0030 §1). Not: `internal` bundles, which `:avvio` cannot build. **Test builder**
(2026-10-11, decided by the user): such a presenter gets ONE test builder in its package's test sources, taking the flat
collaborators (`@Suppress("LongParameterList")` with its reason), so a regrouping touches one function.

**S1:** holders share one `internal` `CorsaSingola` over one `MutableStateFlow`, which owns the synchronous M3 guard,
M1(b)'s generic message and the `inCorso` reset in `finally`.

**Shell:** `apri`/`crea`/`chiudi` share one private single-flight runner (guard, `Caricamento`, the non-fatal-faults
helper, `finally`). The open-project delete holder keeps its own flag, folded into the shell's guard through
`occupata: () -> Boolean`.

**S2:** holders (`FlussoImporta`, `FlussoEliminaRiga`, `FlussoIncontro` with the chevron's `espansi`, `FlussoComandiRiga`
over `ComandiRigaS2`, `LetturaS2` over `LettureS2`) share one `internal StatoS2` over `_stato`, which owns `aggiornaDati`/`aggiornaRiga`, `rigaLibera`,
`azzeraAvviso` and `ricarica` (`::carica`). Keep per-row concurrency and the import guard, with no screen-wide runner.
Initialize `s2` and the holders before `init`. Row commands go through `comandoDiRiga`, which runs
`eccezioneRegistrata(operazione) { suEsito(withContext(io) { comandoConfermato(operazione, comando) }) }`, like S4's
`suRiga`; callers pass the plain port call, and `FlussoImporta`/`FlussoIncontro` keep their own blocks. `LetturaS2` is a pure builder:
`carica` calls it on `io` and keeps the generation counters, the merge and the error state; it takes the player's
availability as `lettore::disponibile` and leaves play/pause to the merge. Merge helpers that touch no presenter state
are file-private functions. Moving code never raises CR-19b's frozen catch counts.

**S3:** holders (`SomiglianzaVoci`, `RevisioneVoci`, `NominaFraseVoci`) built inside `StatoVoci` take its state and its
ONE runner `lanciaComando` as functions; holders read each other through functions; the re-read sets holder state only
through a holder function (`RevisioneVoci.riallinea`); `azioni` binds to the holders.

**Why.** Thin UI and one screen contract (RC-2 in `.mismagent/code-rules.md`): one state and one `azioni`, and a small
home for each flow. Decided: codex (delegated by the user), 2026-10-08/09; the user, 2026-10-11.

**Shown by:** `ui/src/main/kotlin/snastro/ui/progetti/CorsaSingola.kt`, `ui/src/main/kotlin/snastro/ui/progetti/ProgettiPresenter.kt`,
`ui/src/main/kotlin/snastro/ui/ShellPresenter.kt`, `ui/src/main/kotlin/snastro/ui/EliminazioneProgettoAperto.kt`,
`ui/src/main/kotlin/snastro/ui/registrazioni/StatoS2.kt`, `ui/src/main/kotlin/snastro/ui/registrazioni/FlussoComandiRiga.kt`, `ui/src/main/kotlin/snastro/ui/registrazioni/LetturaS2.kt`,
`ui/src/main/kotlin/snastro/ui/registrazioni/RegistrazioniPresenter.kt`, `avvio/src/main/kotlin/snastro/avvio/Presenter.kt`,
`ui/src/test/kotlin/snastro/ui/registrazioni/RegistrazioniPresenterDiProva.kt`, `ui/src/main/kotlin/snastro/ui/parlanti/ParlantiPresenter.kt`,
`ui/src/main/kotlin/snastro/ui/registrazione/RegistrazionePresenter.kt` with `ui/src/main/kotlin/snastro/ui/registrazione/StatoVoci.kt`,
`ui/src/main/kotlin/snastro/ui/registrazione/RevisioneVoci.kt`, `ui/src/main/kotlin/snastro/ui/registrazione/NominaFraseVoci.kt`.
