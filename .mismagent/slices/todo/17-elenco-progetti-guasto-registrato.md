Kind: refactor
Release: R0
Examples:

`ProgettiPresenter.caricaElenco` (`ui/src/main/kotlin/snastro/ui/progetti/ProgettiPresenter.kt:43-60`) catches
`Exception` with `SwallowedException`. It shows only `erroreElenco`, so a corrupt or unreadable registry leaves no
trace in the log. This is R0's own screen (release review R0-2, errors, worst place 3).

What changes:
- `caricaElenco` runs `elenco.progetti()` through `nonFataleRegistrato("carica l'elenco dei progetti") { … }`
  (`ui/.../GuastoNonFatale.kt`). This is a read, so it is not a committing command: ADR 0041 §3 picks
  `nonFataleRegistrato`.
- A null result keeps today's state: `Dati(progetti = emptyList(), erroreElenco = MESSAGGIO_ERRORE_GENERICO)`.
  `withContext(io)` stays inside the block, because the helper is `inline`.
- The raw try/catch and its suppression go.
- `ProgettiPresenter.kt`'s CR-19b frozen count (`architettura-test/.../RegoleArchitetturaliTest.kt`) drops from 1 to 0:
  remove the entry.

Rule served: CR-7, non-fatal-faults, ADR 0041.

Behaviour kept:
- Every test in `ui/src/test/kotlin/snastro/ui/progetti/` stays green, and so does the acceptance suite.
- Add one test: a throwing `elenco.progetti()` logs a WARNING caused by that fault (`registriLogUi`, `causatoDa` in
  `ui/src/test/kotlin/snastro/ui/LogUi.kt`), and the screen still shows `erroreElenco` with Riprova.
- `./gradlew check` green with no new suppression.
