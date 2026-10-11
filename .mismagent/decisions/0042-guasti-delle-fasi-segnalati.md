---
scope: global
status: accepted
supersedes: null
closes_spike: null
decided: 2026-10-11 · architect (design pass R1, release review R1-1, errors=3)
from_note: release review R1-1, worst place 2 (`eseguiFase` drops every phase fault unlogged)
enforced_by: []   # detekt SwallowedException (CR-7) gates the shape once `eseguiFase`'s suppression is dropped; the slice's test gates the report
---
# 0042 — A transcription phase fault is reported through a Trascrizione port, `SegnalatoreGuasti`

## Context
- `eseguiFase` (`trascrizione/applicazione/src/main/kotlin/snastro/trascrizione/applicazione/comandi/EseguiProssimaElaborazioneServizio.kt:390-401`)
  turns every port `Exception` of the pipeline into `null` under `@Suppress("TooGenericExceptionCaught", "SwallowedException")`.
  The caller then picks a fixed `motivo` (`:160-181`, `:267`). The cause reaches no log. Its KDoc gives the reason:
  "this codebase has no logging sink to hand it to" (`:386`).
- That premise is half true. A sink exists: `:supporto`'s `Segnalazione` (`supporto/src/main/kotlin/snastro/supporto/Segnalazione.kt:7`),
  implemented once in `:avvio` as `segnalazioneApp` (`avvio/src/main/kotlin/snastro/avvio/SegnalazioneJul.kt:24-26`).
  But `:supporto` is never reachable from `*:applicazione` (`.mismagent/architecture.md:76`, CR-2), so the service cannot
  take a `Segnalazione` as release review R1-1 suggests.
- CR-7 (`.mismagent/code-rules.md:82-84`): no catch-all that drops the error.
- The user still sees one fixed, plain-Italian `motivo` per fault point (ADR 0003; AC-70). That does not change.
- `PortePipeline` (`trascrizione/applicazione/src/main/kotlin/snastro/trascrizione/applicazione/comandi/PortePipeline.kt:14-20`)
  is the wiring group of the pipeline's ports. Only `:avvio` builds it in `src/main`
  (`avvio/src/main/kotlin/snastro/avvio/trascrizione/ModuloTrascrizione.kt:80`).

## Decision
1. **`:trascrizione:applicazione` `porte/` gains `SegnalatoreGuasti`**, a `fun interface` with one method:
   `guasto(id: RegistrazioneId, motivo: String, causa: Exception)`. `motivo` is the fixed text the Elaborazione fails
   with, so the log line names the phase. Contract: it never throws. A throwing implementation is a caller bug.
2. **`PortePipeline` gains a required `guasti: SegnalatoreGuasti`** with no default (ADR 0030 §1). The service's own
   constructor does not change.
3. **`eseguiFase` takes the id and the `motivo`.** On a port `Exception` it calls `guasti.guasto(id, motivo, e)` and then
   answers `null`. Cancellation and interruption are rethrown as today, and an `Error` is never caught. The
   `SwallowedException` suppression goes. `TooGenericExceptionCaught` stays, with its reason: the boundary takes any
   port fault (ADR 0003).
4. **`:avvio` implements it over `segnalazioneApp`** with one line:
   `Elaborazione di <id> fallita (<motivo>)` and the cause, at WARNING.

## Considered
- **Pass `:supporto`'s `Segnalazione` to the service** (release review R1-1): this breaks CR-2. `:supporto` would become
  reachable from an application module.
- **Log in each ML/audio adapter:** adapters may use `:supporto`, but `eseguiFase` also guards the
  `LettoreRegistrazione` lookup and the `SegnalatoreFase` signals. There would be four or more places, and a new
  adapter could forget.
- **Carry the cause in `RisultatoPipeline.Fallita` up to the dispatcher:** `esegui` returns
  `RisultatoAvanzamento.Avviata(id)` for a failed run too (`:95-96`). The cause would need a new field on a public
  result that the dispatcher (`:avvio`) reads only to grow its exclusion set (AC-313). That costs more for the same log
  line.

## Consequences
- One port more in Trascrizione, and one implementation in `:avvio`. The test constructions of `PortePipeline` (14
  files: 8 in `trascrizione/applicazione/src/test`, 4 adapter tests, 1 in `avvio/src/test`, plus the main wiring) gain
  a recording or a no-op fake.
- A failed transcription now leaves its cause in the app's log (CR-7). The user's `motivo` is unchanged.

## Evidence
- `trascrizione/applicazione/src/main/kotlin/snastro/trascrizione/applicazione/comandi/EseguiProssimaElaborazioneServizio.kt:383-401`
  (read 2026-10-11 at d8905cf6).
- `.mismagent/architecture.md:76` and `:82` (`:supporto` reached only by adapters, `:ui` and `avvio`).
- `trascrizione/applicazione/build.gradle.kts`: no `:supporto` dependency (read 2026-10-11).
- The `PortePipeline(` constructions: `grep -rln "PortePipeline(" --include='*.kt'`, run 2026-10-11. It found 15 files:
  the class itself, `ModuloTrascrizione.kt` and 13 tests.
- Release review `.mismagent/reviews/R1-1.md` (Error handling, worst place 2).
