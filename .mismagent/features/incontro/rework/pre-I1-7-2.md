# pre-I1-7 — cycle 2 (LAST): HIGH from the verifier

Reviewed HEAD: 6890b53829c6e8512f87f1370ed7d183fde2d364 · verifier FAIL (semantic-high). All other lines of cycle 1 are FIXED-OK or NO-CHANGE-OK; do not touch them.

## HIGH — the L32 fix breaks AC-415 in the running app
- ui/src/main/kotlin/snastro/ui/registrazione/StatoVoci.kt:68-69 with :281 — the `incontroId` getter now reads only `vista?.incontroId`.
- In the app the presenter runs on Dispatchers.Swing (Grafo.kt:83): RegistrazionePresenter.init queues `carica()` and then `voci.avvia()`'s collectors; the `comandi.stato` collector runs `rifletti` BEFORE `carica()` returns from `withContext(io) { trascritto() }`. So `rifletti` filters with `incontroId == null`, `mie` is empty, `inCorsoAltrove` stays empty, and nothing calls `rifletti` again when `vista` is set.
- Scenario: confirm a card, leave S3 while the command is pending, come back → the card does not show IN_CORSO/IN_ATTESA, Annulla is not offered, actions look enabled, and on completion `conclusiAltrove` is empty so `ricaricaParlanti` is not triggered.
- Why tests miss it: the ui AC-415 tests (RegistrazioneIdentificazioneTest.kt:328,362) use the same StandardTestDispatcher as scope and io, so `withContext` does not suspend.

## Required
- Keep L32's goal (no UI-thread repository read) AND restore AC-415: e.g. call `rifletti(sorgenti.comandi.stato.value)` again right after `vista` is first set, or start the collectors only after the first load.
- Add an AC-415 test whose `io` is a SEPARATE dispatcher (or a real thread pool) so `withContext` really suspends; show it red at HEAD 6890b538 and green after the fix.
- Also fix the LOW: StatoVociRicaricaTest.kt:142-174 must test with `vista == null` (it passes on old and new code today).
