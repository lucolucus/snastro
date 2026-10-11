# Conventions proposals
- update presenter-split: S3 line — holders (`SomiglianzaVoci`, `CarteVoci`, `ProposteVoci`, `NominaFraseVoci`,
  `RevisioneVoci`) built inside `StatoVoci` take its state and its ONE runner `lanciaComando` as functions (`CarteVoci`
  takes it whole, by reference, for its per-card `suGuasto`; the others take `lanciaComandoGenerico`); a holder's
  background job is its own (`ProposteVoci`: one Proposte job, one cross-Parte job, each cancelling its predecessor);
  `StatoVoci` keeps only the read, the runner and the panel build, with no `TooManyFunctions`; helpers shared by the
  holders live in `DatiParlanti.kt` — `ui/src/main/kotlin/snastro/ui/registrazione/CarteVoci.kt`,
  `ProposteVoci.kt`, `DatiParlanti.kt`, `StatoVoci.kt` (slice 39: the S3 split is complete; cite it in "Shown by")
