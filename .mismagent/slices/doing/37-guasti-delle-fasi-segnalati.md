Kind: refactor
Release: R1
Examples:
Base: 0ecb3f2da3debfb73a041252a244d5e0c99330e4

A failed transcription leaves the user a fixed `motivo` and leaves the log nothing. This is release review R1-1,
error handling 3, worst place 2, and it breaks CR-7.

The defect is in `eseguiFase`
(`trascrizione/applicazione/src/main/kotlin/snastro/trascrizione/applicazione/comandi/EseguiProssimaElaborazioneServizio.kt:383-401`).
It turns every port `Exception` into `null` under `@Suppress("SwallowedException")`. Decision:
[ADR 0042](../../decisions/0042-guasti-delle-fasi-segnalati.md).

What changes (ADR 0042 §1–§4):
- **New port.** `trascrizione/applicazione/src/main/kotlin/snastro/trascrizione/applicazione/porte/SegnalatoreGuasti.kt`
  is a `fun interface` with `guasto(id: RegistrazioneId, motivo: String, causa: Exception)`. Its KDoc states that it
  never throws.
- **`PortePipeline`** (`comandi/PortePipeline.kt`) gains `guasti: SegnalatoreGuasti`, required and with no default.
- **`eseguiFase`** takes the Registrazione id and the `motivo` it maps to. It reports the cause, then answers `null`:
  - Each of its callers (`:160`, `:164`, `:170`, `:175`, `:180`, `:267`) passes the `MOTIVO_*` it already returns.
  - Cancellation and interruption are rethrown as today, and an `Error` is never caught.
  - Drop `SwallowedException` from the suppression. Keep `TooGenericExceptionCaught` with its reason.
  - Rewrite the KDoc: it no longer says "no logging sink".
- **`:avvio` wiring.** `avvio/src/main/kotlin/snastro/avvio/trascrizione/ModuloTrascrizione.kt:80` builds it over
  `segnalazioneApp`, with the message `Elaborazione di <id> fallita (<motivo>)` and the cause.
- **Tests.** The 13 test constructions of `PortePipeline` pass a no-op, or the recording fake where the test checks
  faults. List them with `grep -rln "PortePipeline(" --include='*.kt'`.
- **Map.** `.mismagent/architecture.md`: in the amendment of 2026-10-11, the "Target state … Until then" sentence
  becomes "landed".

How behaviour is kept:
- The suite stays green. The fixed `motivo` of each AC-70 fault point is unchanged.
- New: in `trascrizione/applicazione/src/test/kotlin/snastro/trascrizione/applicazione/comandi/EseguiProssimaElaborazioneServizioTest.kt`,
  the AC-70 fault tests (`:154`, `:166`, `:179`, `:509`) also assert that a recording `SegnalatoreGuasti` got exactly one
  report. It carries the id, the same `motivo` the Elaborazione failed with, and the thrown cause (same instance).
- New: a run that succeeds sends no report.
- An assumption to check at the first `./gradlew detekt`: detekt's `SwallowedException` accepts a caught exception
  passed on to a call. If it does not, keep the suppression with the reason "reported through `SegnalatoreGuasti`
  (ADR 0042)".
- `RegoleArchitetturaliTest` and `verificaDipendenzeModuli` stay green, because no module edge changes.
