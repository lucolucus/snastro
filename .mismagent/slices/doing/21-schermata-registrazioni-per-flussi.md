Kind: refactor
Release: R0
Examples:
Base: 4a2a8d6f3b536fb7f468574310d5e19c70d2d42a

`ui/src/main/kotlin/snastro/ui/registrazioni/SchermataRegistrazioni.kt` has 1503 lines and 41 top-level functions
under a file-level `@file:Suppress("TooManyFunctions")` (`:3`). It is the view half of release review R0-2's worst
simple-design place. The design note says to split it by the flows the presenter now has.

What changes: move the composables into files in the same package, one per flow:
- Incontro: `IncontroItem`, `RigaIncontroItem`, `MetaIncontro`, `AvvioIncontro`, `ColonnaIncontro`.
- Row and its inline fields: `RigaRegistrazioneItem`, `RigaMeta`, `CampoTitolo`, `CampoData`, `CampoOra`, `CampoInline`,
  `CampoDataInModifica`, `ConSuggerimento`, `aOra`, `aData`, `formattaOra`.
- Row status and elaborazione: `ColonnaElaborazione`, `StatoInAttesa`, `StatoInCorso`, `ColonnaCompletata`,
  `ConfermaRitrascrivi`, `AvvioConNumeroPersone`, `BadgeIdentificazione`.
- Row menu and delete: `MenuAzioniRegistrazione`, `VoceMenu*`, `ConfermaElimina`.
- Import and drop zone: `BarraImportazione`, `DropZoneVuota`, `bordoTratteggiato`, `registrazioneDropTarget`,
  `importaScelti`.
- The screen shell (`SchermataRegistrazioni`, loading/error, `ContenutoRegistrazioni`, `ElencoRegistrazioni`) stays in
  the current file.
- Composables shared across files become `internal`, never `public`.
- Remove each file's `TooManyFunctions` suppression if it fits the threshold. Any that remains states its reason, as
  today.
- This is a pure move: no composable changes behaviour, test tag or text.

Rule served: simple design, thin view (RC-2), suppressions shrink.

Behaviour kept:
- Every test in `ui/src/test/kotlin/snastro/ui/registrazioni/` stays green unchanged, and so does the acceptance suite.
- The smoke screenshot `avvio/build/smoke/s2.png` draws the same screen.
- `./gradlew check` green.
