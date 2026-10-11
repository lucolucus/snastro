VERDICT: PASS
SHA: dc571d13dc746ba838793a6e39dc1080b485c09f
SCORES: simple=5 naming=5 modularity=5 duplication=4 concision=5 errors=5 tests=4

# Review 37-guasti-delle-fasi-segnalati, cycle 1 (slice, Base 0ecb3f2d)

Evidence: `mm gate` green (`./gradlew check` + detekt), `mm check --base 0ecb3f2d` ok. The only warnings are pre-existing
"no Evidence: section" lines on older ADRs, none of them in this diff.

| dimension | score | evidence |
|---|---|---|
| Simple design | 5 | `eseguiFase` is now a file-private `PortePipeline` extension (`EseguiProssimaElaborazioneServizio.kt:395-407`): report, then `null`. Each caller passes the `MOTIVO_*` it already returns (`:175-201`, `:269-272`). There is no new layer. |
| Naming | 5 | The new names follow the codebase pattern: `SegnalatoreGuasti.guasto(id, motivo, causa)`, `GuastoSegnalato`, `segnalatoreGuastiSu`, `<Porta>Finta`/`<Porta>Contratto`. |
| Modularity | 5 | The port lives in Trascrizione `porte/` (`SegnalatoreGuasti.kt:10`) and its implementation in `:avvio` over `segnalazioneApp` (`SegnalatoreGuastiSuSegnalazione.kt:13-16`, `ModuloTrascrizione.kt:90`). `:applicazione` gains no `:supporto` edge (CR-2, ADR 0042). |
| Duplication | 4 | The 12 other test constructions each add one `SegnalatoreGuastiFinta()`. That repetition is mechanical, and it follows from the required field with no default (ADR 0030 §1). |
| Concision | 5 | Nothing is unused. The test fakes lost their unused default `guasto` arguments (`EseguiProssimaElaborazioneServizioTest.kt:836-846`). |
| Error handling | 5 | The `SwallowedException` suppression is gone and detekt is green with the cause passed on (`:403`). `TooGenericExceptionCaught` keeps a reason. Cancellation and interruption are rethrown before the catch-all (`:397-401`), and an `Error` is never caught. A lookup miss reports nothing. |
| Tests | 4 | Each of the six AC-70 throw tests asserts exactly one `GuastoSegnalato(id, the same motivo, the same cause instance)` through `verificaGuastoDiPipeline` (`:229-232`). Misses assert an empty list, and so does AC-69 on success (`:135`). New tests: the port contract and the Finta (`SegnalatoreGuastiContratto.kt`, `SegnalatoreGuastiFintaTest.kt`), and the `:avvio` adapter (`SegnalatoreGuastiSuSegnalazioneTest.kt:18-26`: exact log line, `assertSame` cause). |

## Blocking findings
None.

## Checks requested by the caller
- **The `EseguiProssimaElaborazioneServizioTest` move to file level.** Compared side by side against Base: the bodies of
  `servizio`, `pipeline` (gains only `SegnalatoreGuastiFinta()`), `unaVista`, `unaInAttesa` and the eight constants are
  unchanged.
- **No assertion was removed or weakened.** The diff only adds assertions: one in AC-69 and one in
  `verificaGuastoDiPipeline`. The call at `:121` went from named to positional arguments with the same values.
- **The test fakes' exception messages changed** (for example "guasto di prova in tutti()" became "guasto di prova in
  una porta"). I grepped the test for those strings, and no assertion reads any of them.

## Slice conformance
- The diff matches ADR 0042 §1–§4. The log message is exactly `Elaborazione di <id> fallita (<motivo>)`, and
  `segnalazioneApp` logs at WARNING when there is a cause (`SegnalazioneJul.kt:24-26`).
- `grep -rln "PortePipeline("` finds 13 test files, the class and the `:avvio` wiring. All 13 tests pass the Finta.
- The architecture map amendment now says "landed" (`.mismagent/architecture.md:291-292`).
- The first service-side use of the non-fatal-faults shape is proposed in `.mismagent/conventions-proposals.md:2`, and
  the proposal matches the diff.

## Advice (not blocking)
- `verificaInterruzioneNonGuasto` (F4, `EseguiProssimaElaborazioneServizioTest.kt:~605`) could also assert
  `guastiRicevuti()` is empty. That would pin down that cancellation and interruption are not reported as port faults.
- The `SegnalatoreGuastiContratto` "never throws" contract holds only because `segnalazioneApp` delegates to JUL. If a
  future implementation threw inside `eseguiFase`'s catch, the exception would escape the pipeline. The ADR already
  calls that a caller bug, so this is acceptable.
