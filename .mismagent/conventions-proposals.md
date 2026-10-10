# Conventions proposals

- update presenter-split: S2 row runner (2026-10-09 "slice 33 target") is now as built — `StatoS2` receives `io`;
  `comandoDiRiga(id, operazione, comando: () -> Esito<Unit>, suEsito, suGuasto)` runs
  `eccezioneRegistrata(operazione) { suEsito(withContext(io) { comandoConfermato(operazione, comando) }) }`, so
  `suEsito` runs on the presenter dispatcher; the 7 callers pass only the port call and name the operation once;
  `FlussoImporta`/`FlussoIncontro` keep their own blocks (not row commands). Drop "slice 33 target" from the heading —
  `ui/src/main/kotlin/snastro/ui/registrazioni/StatoS2.kt`, `RegistrazioniPresenter.kt`, `FlussoEliminaRiga.kt`
  (one shape for S2's and S4's row runners, R0-3 duplication).
