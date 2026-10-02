# Rework 1 — rigenerazione-sbobinatura-incontro

Reviewed HEAD: 2690bafc4f0542f1f2a0305744f053b143796d96 · verifier FAIL (ac-coverage) · code-review APPROVE

## FAIL — AC-I35 "every transcribed Parte **of i**" is not discriminated
- A mutant of `AbbonatoSbobinaturaEventi.accodaParti` that queues `registrazioniConTrascritto()` (every transcribed Registrazione of the Progetto) survives the whole `:sbobinatura:adattatori:test` suite.
- `AC-I35 VociUnite rigenera ogni Parte trascritta dell Incontro e nessuna di un altro` (AbbonatoSbobinaturaEventiTest.kt:362) uses `ambienteDueParti()` with `altre = emptyMap()`: no other Incontro exists, so "nessuna di un altro" is never exercised. The test at :377 also passes under the mutant.
- Required: put a transcribed Registrazione of ANOTHER Incontro in that test's fixture (as the rename test at :427 does with `altra`) and assert it is not written; do the same for at least one more Incontro-keyed event (VoceDivisa, SegmentoRiassegnato or AttribuzioneConfermata). Prove the mutant above is killed (run it in a scratch copy outside the repo, then delete it).

Not in scope of this rework (logged in pre-release.md): the MED on the ModuloSbobinatura wiring swap, the LOW on the INV-23 test.
