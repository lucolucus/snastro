Kind: refactor
Release: R0
Examples: EX-1, EX-9
Base: b6d474cfec56ef63ae2518bec1e18a9803325450

Two small alignments after slice 02 (decided: codex, delegated by the user, 2026-10-08):
- the delete-project confirmation (`MESSAGGIO_CONFERMA_ELIMINA_PROGETTO` in `ui/.../testi/TestiProgetti.kt`) becomes exactly:
  "Vengono cancellati per sempre i contenuti del progetto gestiti da snastro, comprese registrazioni, trascrizioni e
  riassunti. Gli altri file nella cartella restano." — the old text claimed the whole folder goes, false since EX-9;
- `ProgettiPresenter`'s `cartellaUtente` loses its `""` default (conventions topic display-paths): every caller,
  the EX-1..3 acceptance test included, passes a home or an explicit `""`.
