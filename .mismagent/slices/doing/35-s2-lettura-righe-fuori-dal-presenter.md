Kind: refactor
Release: R1
Examples:
Base: 93d8e8c00dac4f003b949141d086b24bda62a4ec
After: 22, 23, 24, 25

After slice 34, `RegistrazioniPresenter` still mixes two jobs (release review R0-3, simple design 3):
- reading and building the rows: `costruisciLista`, `gruppi`, `costruisciRiga`, `elaborazioneDi`,
  `identificazioneDi`, `conteggiIdentificazione`, `precompilato`, `numeroPersonePrefillDi`, `eliminazioneDi`
  (`ui/src/main/kotlin/snastro/ui/registrazioni/RegistrazioniPresenter.kt:268-446`);
- merging the rows into the screen and playing them back.

The read side has its own six collaborators: `registrazioni`, `incontri`, `statiElaborazione`,
`identificazioniIncontri`, `posizioniNellaCoda`, `numeroPersonePrecompilato`. It also uses `clock` (`:424`).

What changes (presenter-split, "Collaborators of an extracted flow"):
- A new `internal` class, `LetturaS2`, builds `ListaS2` from one public bundle, `LettureS2`. The bundle holds those
  collaborators (and `clock` if only the read side uses it), all required and non-null.
- `LetturaS2` is a pure builder: the generation counters (M1, L485b), the merge (`aggiornaConNuoveRighe`,
  `fondiIncontri`) and the error state stay in the presenter. It is the presenter's `carica` that calls it on `io`.
- `riproduzioneDi` and the player reflection stay with the merge, under L478a: playback is re-derived at merge time.
  `costruisciRiga` still needs the player's availability, so pass it in.
- The presenter takes `letture: LettureS2` (16 → about 11 parameters).
- `avvio/src/main/kotlin/snastro/avvio/Presenter.kt` builds `LettureS2`.
- Drop `TooManyFunctions` if detekt allows. Keep `LongParameterList` only with a stated reason (detekt's default
  constructor threshold is 7, `config/detekt/detekt.yml:37-40`). Drop the redundant `private val io = io`
  (`:122`, release review R0-3, concision).
- Update `.mismagent/architecture.md` and `ARCHITECTURE.md` if the S2 map changes.

How behaviour is kept:
- The suite stays green with the assertions unchanged. The load, merge and generation tests
  (`RegistrazioniPresenterAggiornamentoTest`, `RegistrazioniIdentificazioneTest`, `RegistrazioniIncontriTest`,
  `RegistrazioniPresenterConcorrenzaTest`) run through `stato`.
- If a row-building rule has no test through the presenter, add a characterization test for it before the move: the
  queue position (ADR 0023 §4), the identification badge (AC-204/AC-345), and the elapsed time from `clock`.
- The smoke screenshots stay byte-identical to Base.
