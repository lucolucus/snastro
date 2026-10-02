# Rework 1 — viste-parte-incontro

Reviewed HEAD: cbc84cf7e50ada0ffcc8124a7497da988cef6284 · verifier FAIL (ac-coverage)

## FAIL — AC-I44 second clause not falsifiable
- Test `AC-I44 la prima trascrizione di una Parte appena importata non rende sola lettura` opens an Elaborazione on RegistrazioneId("p4"), but the `treParti()` fixture's `ordine` is listOf(P1,P2,P3): p4 is never in `parti(INCONTRO)`, and TrascrittoQuery.kt:41-43 only iterates over `parti`. The test passes with or without the `radice.haParte(p.registrazioneId)` filter.
- Required: put p4 in `ordine` (listOf(P1,P2,P3,p4)) with NO completaParte for it and an open Elaborazione on it; assert `vista(P1).solaLettura == null` and p4's `numero` is 4 in `parti`. Show the test fails when the haParte filter is removed (scratch edit, reverted), then green.

Not in scope (logged in pre-release.md): defaults on TrascrittoView, N queries per view, tautological check in AC-I43.
