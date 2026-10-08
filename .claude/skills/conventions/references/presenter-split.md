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

**S2 · Registrazioni** (added 2026-10-08, decided: codex on delegation): S2 holders (`FlussoImporta`, `FlussoEliminaRiga`)
share one `internal StatoS2` over `_stato`, owning `aggiornaDati`/`aggiornaRiga`, `rigaLibera`, `azzeraAvviso` and
`ricarica` supplied as `::carica`; preserve S2's per-row concurrency and import guard, with no screen-wide single-flight
runner; initialize `s2` and the holders before `init`; bind shared-state-only actions directly
(`chiudiAvviso = s2::azzeraAvviso`). Slice 16 redistributed the two existing CR-19b catch counts without raising the
total (not a general permission to transfer frozen allowances); keep cancellation/Error propagation and `finally` cleanup.
Their unlogged causes remain non-fatal-faults/CR-7 debt, not an approved exception
(`ui/src/main/kotlin/snastro/ui/registrazioni/StatoS2.kt`).

**S2 row runner** (added 2026-10-08, ADR 0041, decided: codex on delegation; lands with slice 18): S2 holders and the
presenter share one per-row command runner on `StatoS2`; never a try/catch runner per command. It owns the synchronous
row guard, the in-flight flag, `azzeraAvviso`, the launch, the logged generic `erroreRiga` through `eccezioneRegistrata`,
and the reset in `finally`; callers supply the command and the success/refusal handling, preserving per-row concurrency
and the separate import guard.

**S2 row runner, as built** (slice 18, decided: codex on delegation): `StatoS2.comandoDiRiga(id, operazione, comando,
suEsito, suGuasto)`; callers keep `withContext(io) { comandoConfermato(…) }` inside `comando` and pass one `suEsito` for
Ok and refusal (the presenter's plain commands share `concludiRiga`) — `ui/src/main/kotlin/snastro/ui/registrazioni/StatoS2.kt`,
`ui/src/main/kotlin/snastro/ui/registrazioni/RegistrazioniPresenter.kt`, `ui/src/main/kotlin/snastro/ui/registrazioni/FlussoEliminaRiga.kt`.

**Why.** Thin UI and one screen contract (RC-2 in `.mismagent/code-rules.md`): the screen keeps one state and one
`azioni` while each flow gets its own small home. A lambda in `azioni` or a forwarding member only hides the size.
Other screens keep their own concurrency (e.g. the audio of `RegistrazionePresenter`). Not yet uniform practice:
some presenters still grow with an explicit suppression; new growth splits instead.
Decided: codex (delegated by the user), 2026-10-08.

**Shown by:** `ui/src/main/kotlin/snastro/ui/progetti/CorsaSingola.kt`, `ui/src/main/kotlin/snastro/ui/progetti/FlussoEliminazione.kt`,
`ui/src/main/kotlin/snastro/ui/progetti/ProgettiPresenter.kt`; an earlier split with shared state:
`ui/src/main/kotlin/snastro/ui/registrazione/RegistrazionePresenter.kt` with `ui/src/main/kotlin/snastro/ui/registrazione/StatoVoci.kt`.
