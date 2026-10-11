# Conventions proposals
- update screen-split: Shown by gains S3 — `ui/src/main/kotlin/snastro/ui/registrazione/SchermataRegistrazione.kt`
  (the shell), `ui/src/main/kotlin/snastro/ui/registrazione/SegmentoItem.kt`,
  `ui/src/main/kotlin/snastro/ui/registrazione/IntestazioneRegistrazione.kt`,
  `ui/src/main/kotlin/snastro/ui/registrazione/BarraSelezioneVista.kt` (a long composable split into its controls, no
  `LongMethod`), `ui/src/main/kotlin/snastro/ui/registrazione/CartaVoceVista.kt` (reasoned residual suppression),
  `ui/src/main/kotlin/snastro/ui/registrazione/MenuParlanti.kt` (menus shared across files, `internal`) — slice 40
  (S3 now follows the rule S2 shows).
