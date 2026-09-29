# Rework 1 — c3-composizione-piatta (verifier FAIL: gate, head a5394e69)

## FAIL — a new flake in AC-C58 under load
- `ComposizioneTrascrizioneTest > AC-C58 un interrompi guasto durante lo spegnimento non impedisce di chiudere il database e rilasciare il lock()` FAILED with `AssertionFailedError: l'interrompi guasto e segnalato come sfuggito` at ComposizioneTrascrizioneTest.kt:459.
- When it happened: in a full `./gradlew check --no-build-cache --rerun-tasks`. It passes alone (6/6) and in some full :avvio reruns. This block moved and rewrote the test, from r1/ComposizioneR1Test.
- The verifier's reading of the cause:
  - `SessioneProgettoImpl.chiudi` cancels the project scope FIRST, so `runInterruptible` interrupts the fake source's `Thread.sleep` (test line 435) and CodaCondivisa sets `inCorso = null` in its `finally`.
  - ArrestoProgetto then hops to a new thread before `coda.ferma()` → `fermaEAttendi`. If `inCorso` is already null, `interrompi()` is never called and nothing is reported as "sfuggito".
  - The new thread hop widens a race that the old same-thread sequence kept narrow.
- Required:
  1. Reproduce it red deterministically: force the interleaving, or stress-run it, and show the evidence.
  2. Decide WHERE the defect is and say why:
     - (a) test only: the fake `prossima` should ignore interrupts, as an LLM run does;
     - (b) production shutdown order: should `chiudi` run ArrestoProgetto (fermaEAttendi → interrompi) BEFORE cancelling the scope, so the running source's `interrompi` is always reached? Check this against ADR 0017 §3, ADR 0023 §5 and AC-S63/S162. `interrompi` must reach a running Riassunto whose thread may ignore interrupts.
     If (b) is a real defect, fix production and keep the test strict. Do not weaken the assertion.
  3. Prove stability: the full `./gradlew check --no-build-cache --rerun-tasks` green, plus at least 10 :avvio test reruns with no AC-C58 failure.
- Scope: only this; no other refactoring. `grep -rn PROBE` must be empty before you return. Probes only on a throwaway copy.
