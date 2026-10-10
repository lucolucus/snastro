Kind: refactor
Release: R1
Examples:
Base: 0a605fbff920c8702ee6509f2b3934645b7d14ca
After: 22, 23, 24, 25

Since slices 26 and 30 every `:ui` edge logs its fault, but four of them still show the user nothing: the rule
"Message at every edge" (conventions non-fatal-faults, 2026-10-09) asks for M1(b)'s generic message there too,
keeping the follow-up. One of them, the player's `pausa`, has no edge at all. The Riassunto tab goes first because
R1's feature slices 22–25 change it.

What changes (ADR 0041 §3, non-fatal-faults):
- **Riassunto.** `ui/src/main/kotlin/snastro/ui/riassunto/RiassuntoPresenter.kt`.
  - `scaricaModello` (`:305`): on a null from `nonFataleRegistrato` it re-reads (`ricarica()`), then sets
    `MESSAGGIO_ERRORE_GENERICO` the same way `riassumi` does (`:290-294`: after `ricarica()`, which clears it).
  - `apriFonte` (`:399`): on a null, when the source is this Parte (`parte == registrazioneId`), show the generic
    message. When it is another Parte the switch still happens (L194: a failing playback never stops the switch),
    and that switch may dispose this presenter, so the message cannot follow. Leave it and add one line to
    `.mismagent/design-notes.md` for it.
- **Settings.** `ui/src/main/kotlin/snastro/ui/impostazioni/ImpostazioniPresenter.kt:63` `leggiSenzaErrori`: keep
  the `Preferenze()` fallback, and start `_stato` with `errore = MESSAGGIO_ERRORE_GENERICO` when the read failed.
  `chiudiErrore` already clears it.
- **Player.** `ui/src/main/kotlin/snastro/ui/lettore/LettorePresenter.kt:136` `pausa`: today a throwing
  `lettore.pausa()` escapes into `scope` unlogged (read from the code, no test pins it). Run it through
  `nonFataleRegistrato("pausa")` on the same single lane (`io`). On a fault, set `LettoreUiStato.Errore(MESSAGGIO_ERRORE_GENERICO)`
  (L471d: the play control stays enabled), but only if no newer request has started (`richiestaCorrente`
  unchanged), the same check `avvia` makes.

How behaviour is kept:
- The existing tests of the three presenters stay green unchanged. Add the new tests to the sibling classes
  (`RiassuntoPresenterGuastiTest` over `AmbienteRiassunto.kt`, progress.md slice 26: `RiassuntoPresenterTest` is at
  detekt's `LargeClass` limit).
- One test per site, red before the change: the message is shown, the follow-up still happens (the re-read; the
  fallback values), and the log line is checked with `registriLogUi`/`causatoDa`.
- For `pausa`, one test that a throwing port leaves `scope` alive (the `stato` collector still works) and shows the
  error.
- CR-19b stays at 0 for these files: no raw catch.
