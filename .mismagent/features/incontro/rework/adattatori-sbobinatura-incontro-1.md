# Rework 1 — adattatori-sbobinatura-incontro

Reviewed HEAD: 8f1bf7d5735d76530942c04923a6a6c022170c99 · verifier FAIL (semantic-high)

## FAIL — AC-183 weakened by a test reorder
- With the new two-stage fan-out (Chiave.PerIncontro listed inside ritenta, then PerRegistrazione keys), a burst ElaborazioneCompletata(REG_1), AttribuzioneConfermata, VociUnite (original AC-183 order) yields 2 writes: PerRegistrazione(REG_1) runs first and writes, then the PerIncontro fan-out re-queues REG_1 and writes again.
- AC-183: "N eventi della stessa Registrazione in rapida successione producono una sola scrittura" — no ordering condition. Reordering the burst in AbbonatoSbobinaturaEventiTest.kt (~151-160) hides the regression.
- Required: restore exactly-one-write coalescing for ANY ordering of the burst (and duplicates), keeping the Parti listing off the committing thread and inside the retried unit. E.g. run pending PerIncontro units before PerRegistrazione units in the worker, or have the PerIncontro fan-out merge into a still-pending PerRegistrazione entry instead of adding a second run. Restore AC-183's original order and add a permutation test (both orders + a duplicate) asserting exactly 1 write. Do not relax AC-183.

Not in scope (logged in pre-release.md): no test of the ModuloSbobinatura TrascrittoEliminato subscription.
