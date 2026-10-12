# Screen split

**Rule.** For new screen growth beyond detekt's `TooManyFunctions` threshold, split by cohesive flow into same-package
files named after their main composable; keep the public entry and the screen orchestration in `Schermata<X>.kt`.
Extracted helpers needed across files are `internal`, otherwise `private`; screen-shared measures stay with their owner
as `internal`. Remove unnecessary suppressions; keep a reasoned file-level suppression only for a cohesive file still
over the threshold. Existing layouts need not be renamed solely for conformity.

A file-level suppression's reason states this file's own cohesion, never "same as <other file>" (2026-10-12,
decided by the user).

**Why.** The view half of presenter-split: each flow gets a small home while the screen keeps one entry, and the thin-UI
rule (RC-2 in `.mismagent/code-rules.md`) holds. It describes future practice, not an already uniform layout: some
screens expose a differently named main composable (e.g. `SezioneSomiglianza`, `PannelloVociVista`).
Decided: codex (delegated by the user), 2026-10-08.

**Shown by:** `ui/src/main/kotlin/snastro/ui/registrazioni/SchermataRegistrazioni.kt` (the shell),
`ui/src/main/kotlin/snastro/ui/registrazioni/IncontroItem.kt`, `ui/src/main/kotlin/snastro/ui/registrazioni/RigaRegistrazioneItem.kt`
(reasoned residual suppression), `ui/src/main/kotlin/snastro/ui/registrazioni/ColonnaElaborazione.kt`,
`ui/src/main/kotlin/snastro/ui/registrazioni/MenuAzioniRegistrazione.kt`, `ui/src/main/kotlin/snastro/ui/registrazioni/BarraImportazione.kt`; S3 (slice 40): `ui/src/main/kotlin/snastro/ui/registrazione/SchermataRegistrazione.kt` (the shell),
`ui/src/main/kotlin/snastro/ui/registrazione/SegmentoItem.kt`, `ui/src/main/kotlin/snastro/ui/registrazione/IntestazioneRegistrazione.kt`,
`ui/src/main/kotlin/snastro/ui/registrazione/BarraSelezioneVista.kt` (a long composable split into its controls),
`ui/src/main/kotlin/snastro/ui/registrazione/CartaVoceVista.kt` (reasoned residual suppression),
`ui/src/main/kotlin/snastro/ui/registrazione/MenuParlanti.kt` (menus shared across files, `internal`).
