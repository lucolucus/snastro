# Rework 1 — avvio-coda-condivisa (reviewed head 9e51176; code follows ADR 0023 §1/§2/§4 + D-0005)

## FAIL 1 — AC-S63 stop half (spec updated by user decision D-0006)
`FonteCoda` gains an 8th field `interrompi: () -> Unit = {}`. `CodaCondivisa.fermaEAttendi` (CodaCondivisa.kt:125-127 today only
joins) must call the RUNNING item's source `interrompi` before/while interrupting the worker; with nothing running, no source's
interrompi is called. The Elaborazione source leaves it a no-op. Test it (the running source's interrompi called exactly once on stop;
none when idle). Update the KDoc that still calls `annulla` a "DEVIATION" (accepted by D-0005).

## FAIL 2..5 — multi-source clauses untested (add tests with TWO sources)
- AC-S58 2nd half: R@t1 before a non-empty E@t2 → the R claim receives primaDi = t2 (record the bound in the fake; fails if null is passed).
- AC-S59: after an E claim answers Nessuno, a waiting R item still runs in its global-order turn (not overtaken).
- AC-S61: a stuck R item excluded per source id while E items and later R items keep draining; the exclusion never leaks to the other source's ids.
- AC-S57 2nd half: two items of the SAME kind at the same millisecond keep their own source order (id).

## User-approved extra (two resilience MEDs, 2026-09-26)
- CodaCondivisa.kt:186: bound the AC-S59 immediate re-tick — ONE immediate re-tick after a non-empty-peek Nessuno, then fall back to
  INTERVALLO_CONTROLLO (test: a source whose teste and prossima disagree does not spin; e.g. count prossima calls over a short window).
- CodaCondivisa.kt:157-158,167: run the peek (`fonte.teste`, `trattenuta()`) inside `eseguiProtetto` so a throwing read is handled like
  an ordinary escape and the queue keeps running (test: a source whose teste throws once — the queue survives and later drains).
Nothing else.
