# Rework 2 (LAST cycle) — c3-composizione-piatta (verifier FAIL: gate, head faaf1029)

Rework 1's AC-C58 fix was verified as correct and discriminating. Keep it.

## FAIL — AC-S145 timed out in the full no-cache gate
- `./gradlew check --no-daemon --max-workers=2 --no-build-cache --rerun-tasks` in a fresh clone at faaf1029 failed:
  `ComposizioneSintesiTest > AC-S145 all'avvio i due recuperi girano prima del primo reclamo() FAILED`, AssertionError at ComposizioneSintesiTest.kt:149 (`it.attendiPronto(b)`, 10 s bound at AmbienteProgetto.kt:261).
  So b's Riassunto was not ready within 10 s.
- The test passed in 3 separate `:avvio:test --rerun-tasks` runs, at about 2 s each.
- AC-C71 pins "AC-S145 green on the single composition", so this is this block's own evidence. The body is unchanged from r3/ComposizioneR3Test; the composition under it is new.
- Required:
  1. Decide, with evidence, between:
     - (a) a timeout under load: the time to the first claim sits close to the poll ticks;
     - (b) a MISSED WAKE-UP in the new composition (Campanello / recupera() at open / avvia skipping the first recovery pass, D-c; AC-C70/C71), where the queue sleeps until a poll tick instead of being woken.
     Instrument on a throwaway copy: time open → first claim → pronto. Stress-run AC-S145 alongside a CPU hog, or run the whole `check`.
  2. If (b): fix production. The wake-up must not depend on a poll tick after the synchronous recovery and avvia. Add a test that fails without the fix, for example a Campanello missed before `avvia` subscribes.
  3. If (a) only: do NOT just raise the timeout blindly. Show the time budget, then justify the bound or make the test deterministic.
  4. Stability proof: `./gradlew check --no-daemon --max-workers=2 --no-build-cache --rerun-tasks` green TWICE, plus 10 `:avvio:test --rerun-tasks` runs with no AC-S145 or AC-C58 failure.
- This is the last rework cycle. Re-read rework 1 as well, and do not regress it. Probes only on a throwaway copy; `grep -rn PROBE` must be empty when you finish.
