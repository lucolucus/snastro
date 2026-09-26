# Rework 1 — modelli-provisioning-facoltativo (reviewed head ea49b39b)

## FAIL (verifier — AC-S29 concurrency clause not proven)
- `modelli/src/test/kotlin/snastro/modelli/ProvisioningModelliTest.kt:436`, test
  `AC-S29 due chiamate concorrenti a scarica(id) sono serializzate sullo stesso lucchetto`: two Callables on a
  newFixedThreadPool(2) with NO start barrier, asserting only the final installed content — it would pass with the lock removed.
  **Fix:** both Callables `await()` a shared `CyclicBarrier(2)` immediately before `scarica(id, …)`, and assert the
  serialization itself (e.g. the fake server sees non-overlapping requests / at most one in flight, or the second call
  observes the first's completed install), so the test goes RED if the lock is removed (prove it by temporarily removing it).
Nothing else. The :avvio stopgap in ServizioModelliProvisioning.mappaErrore was judged acceptable — keep it.
