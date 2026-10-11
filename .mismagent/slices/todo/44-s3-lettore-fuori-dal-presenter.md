Kind: refactor
Release: R2
Examples:
After: 41

`ui/src/main/kotlin/snastro/ui/registrazione/RegistrazionePresenter.kt` (459 lines, 28 `fun`) still carries
`@Suppress("LongParameterList", "TooManyFunctions")` at `:71` (release review R1-2, simple design 4, first of the
"next candidates"). Its biggest flow is the playback lane, mixed with the read path and the tabs:
- state: `ioLettore` (`:97`), `comandoLettoreInCorso` (`:106`), `disponibilitaIncerta` (`:112`);
- functions: `inRiproduzione` (`:312`), `barraDi` (`:318`), `rifletti` (`:329`), `riproduciSegmento` (`:341`),
  `riproduciDaInizio` (`:350`), `pausa` (`:357`), `avviaLettore` (`:386`), `riproduciEstrattoVoce` (`:392`),
  `riproduciEstratto` (`:402`).
Why now (judgment): R2 is "ricerca per parole con anteprima e salto al punto" (`.mismagent/brief.md:93`); a jump to a
point plays the Registrazione from a position, so R2 is likely to change this lane. A small home for it first keeps
that change out of a 459-line presenter.

What changes (presenter-split):
- **An `internal` holder for the lane** in `snastro.ui.registrazione` (e.g. `LettoreRegistrazione`), built inside the
  presenter like `StatoVoci`. It owns the three fields above and the nine functions; `azioni` binds the five playback
  actions straight to it (`riproduciDaInizio`, `pausa`, `riproduciSegmento`, `riproduciEstrattoVoce`,
  `riproduciEstratto`), no forwarding member left on the presenter.
- It takes what it reads as functions: `registrazioneId`, `lettore`, the shown `Dati` (`audioDisponibile`,
  `segmenti`), the Incontro of the shown trascritto (`voci.vista?.incontroId`), `parlanti::estratto`, the presenter's
  `aggiornaDati`, and the presenter's one fault-handling runner `eseguiSuLane` (`:374`) by reference, so the
  "a fault shows the generic message" path stays in one place.
- `carica` keeps setting `disponibilitaIncerta` through a holder function (no direct write); `segmentiDi` asks the
  holder for `inRiproduzione`; the `lettore.stato` collector in `init` calls the holder's `rifletti`.
- The comments for L573a/L573b/L573d move with their code unchanged.
- Helpers that touch no presenter state (`soloLetturaDi`, `bannerSolaLettura`, `messaggioParteSenzaTrascritto`, if
  pure) become file-private top-level functions.
- The holder carries no `TooManyFunctions` (presenter-split). On the presenter, drop `TooManyFunctions` if
  `./gradlew :ui:detekt` allows it; otherwise keep it and record the function count detekt reports. The read path
  (`carica` … `disponibileOSicuro`) stays: it is the next candidate (design note).

How behaviour is kept:
- No characterization test is needed if the lane is already pinned through `azioni`: check that L573a (one lane,
  a queued command dropped), L573b (thrown `disponibile` → `Errore` until a tick) and AC-208/AC-217/AC-403 each have a
  test in `ui/src/test/kotlin/snastro/ui/registrazione/`; write the missing one first, green before the move.
- The suite stays green with its assertions unchanged; the presenter's constructor and `:avvio` are untouched.
- The smoke renders stay identical to Base from a `git archive` copy (compare after slice 41 has made `s3-parte-2`
  deterministic).
