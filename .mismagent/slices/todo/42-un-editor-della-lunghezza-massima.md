Kind: refactor
Release: R2
Examples:

The lunghezza massima editor exists twice, and the two copies disagree (release review R1-2, duplication 4):
- S3: `ui/src/main/kotlin/snastro/ui/riassunto/RiassuntoPresenter.kt:357-420` (`modificaLunghezzaMassima`,
  `cambiaLunghezzaMassima`, `salvaLunghezzaMassima`, `mostraErroreLunghezzaMassima`, `imposta`,
  `annullaLunghezzaMassima` at `:434`, plus the `var`s at `:118`, `:128-131`) parses `corrente.valore.toIntOrNull()`.
- Impostazioni: `ui/src/main/kotlin/snastro/ui/impostazioni/LunghezzaRiassuntoPresenter.kt:55-82` parses
  `attuale.testo.trim().toIntOrNull()`. So " 500" is saved in one and refused in the other.
- Both run the same command, the same range check, the same `Esito` → message mapping and the same
  `eccezioneRegistrata` edge.

What changes (presenter-split; non-fatal-faults):
- **One rule, one place**: an `internal` file in `snastro.ui.impostazioni` (the Impostazioni presenter owns the
  setting) with
  - `lunghezzaMassimaDa(testo, minimo, massimo): Int?` — trims, parses, checks the range;
  - one suspend save step: runs the command through `eccezioneRegistrata(operazione)` on `io` and answers `null` on
    `Ok`, else the message to show (`messaggioPer(errore)`, or `MESSAGGIO_ERRORE_GENERICO` on a fault).
  Both presenters call them. The trimming rule is Impostazioni's (it is what the domain does for the Argomento,
  `RiassuntoPresenter.kt:258-262`); no AC fixes whitespace (AC-S138, `features/sintesi/building-blocks.yaml:978`).
- **S3's editor leaves `RiassuntoPresenter`**: an `internal` holder in `snastro.ui.riassunto` (e.g.
  `LunghezzaMassimaRiassunto`) owns `lunghezzaMassimaLocale`, the last value read, the bounds, the in-flight guard and
  the six actions; `azioni` binds straight to it. It takes the presenter's `aggiornaDati`/`ricarica` as functions.
  `ricarica` hands it the values read; `costruisci` asks it for the `LunghezzaMassimaUiStato` to show. The "Salvato for
  2 s, unless reopened" timing (`:390-399`) moves unchanged.
- Each UI state keeps its own shape (`LunghezzaMassimaUiStato` on S3, `LunghezzaRiassuntoUiStato` in Impostazioni):
  the views are not touched.

How behaviour is kept:
- Tests first: in `RiassuntoPresenterTest`, " 500 " with bounds 300..2500 sends `500` (red before: refused); in
  `LunghezzaRiassuntoPresenterTest`, the same case (green before, pins the shared rule).
- Every existing test stays green with its assertions unchanged, including `RiassuntoPresenterGuastiTest` (both
  D-0065 saves) and `LunghezzaRiassuntoPresenterTest`.
- `RiassuntoPresenter` keeps its constructor (ADR 0030 §1; `:avvio` untouched). Its `TooManyFunctions` may stay (the
  remaining split is a design note); report the function count `./gradlew :ui:detekt` gives without it.
