# Rework a1-supporto-test-adozione — cycle 2 of 2, LAST (own evidence on 5b1a302: AC-C42 not met)
Re-read BOTH findings files (-1 and -2) before touching code. Cycle 1 fixed both HIGHs: the 10 s :avvio budget is
restored, and AC-536 (RegistrazioneSomiglianzaTest) is deterministic — 20/20 green under load. KEEP those.

## FAIL — AC-C42: RitrascriviR2Test is still not deterministic (worker's own evidence: 16/20 green under load)
AC-C42 requires AC-458/459/479 to pass 20 consecutive `--rerun-tasks` runs. Three distinct failures, all to be fixed
at the ROOT (a longer timeout is NOT a fix):
1. AC-459 content mismatch (assertContentEquals 93 vs 108, "**Voce 2**" vs "**Ospite del 01/01/2026**"): `prepara()`
   captures its Istantanea baseline before Voce 2's "salta" document write has landed (it waits for Voce 1/"Mario"
   only). Make the setup wait for EVERY write it depends on before taking the baseline.
2. AC-458 at RitrascriviR2Test.kt:97 (`numeroPersoneRicevuti.last()` ≠ di(2)): root-cause it (likely the same class:
   an assertion on the last value of a list a background worker is still appending to / an earlier run's value).
3. AC-479 at :172 timing out at 10 s under heavy load: find what the wait depends on (a real-thread queue step?) and
   make the step deterministic or observable (latch/fake signal, OrologioFinto, a queue-idle hook already in the
   test Ambiente) rather than polling wall-clock time under load.
Production code may be touched ONLY if a root cause is a real product bug — then say so explicitly in DEVIATIONS
(it becomes a contract-level question for the composer).

## Evidence required
`./gradlew :avvio:test --tests '*RitrascriviR2Test' --no-daemon --max-workers=2 --rerun-tasks` × 20, all green, with
the load average noted; then the full gate.
