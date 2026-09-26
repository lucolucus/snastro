---
id: "schede-registrazione"
type: "ui"
context: "ui"
side: "app"
wave: 5
release: "R3"
module: ":ui (snastro.ui.registrazione)"
consumes:
  - "tec-shell-ui"
  - "ui-kit-sintesi"
depends_on:
  - "stile-sintesi"
related_adrs:
  - "0018"
  - "0021"
tests_nl_status: "draft"
consumes_rm: []
triggers: []
---
# schede-registrazione — S3 · schede Trascrizione | Riassunto nella colonna centrale + precedenza dei banner

## What to do
Rework of the S3 recording page (merged schermata-registrazione / restyle-registrazione): the centre column gets the SchedeSn tabs Trascrizione (default, today's body) | Riassunto, the Riassunto tab hosting a content slot supplied by the composition (optional input, like AC-342: absent in R1/R2 → no tabs); the right panel keeps Voci only; the tab choice is kept per window; a status mark after the 'Riassunto' label; one screen Banner at most with the ux precedence.

Note: Id pinned as `schede-registrazione` (architect/ux called it `schermata-registrazione`, which collides with the sibling's merged block id — R19-7 accepted by the user 2026-09-25). The ux surface 'selected tab kept per window' and 'status mark' land here; the tab CONTENT is scheda-riassunto.

## Tasks
- AC-S119 Built without the Riassunto slot (R1/R2 compositions) S3 is unchanged: no tabs, every existing S3 presenter/render test green unmodified
- AC-S120 With the slot and a Trascritto: tabs 'Trascrizione' (selected by default) and 'Riassunto'; selecting Riassunto shows the slot; the right panel shows only Voci (no Riassunto tab there)
- AC-S121 The selected tab is kept per window while navigating between recordings: select Riassunto on A, open B → Riassunto selected (presenter test)
- AC-S122 Status mark after 'Riassunto': Clock while the open request is InAttesa, pulsing dot while InCorso, none otherwise (fed by the optional segno flow)
- AC-S123 Banner precedence table test: read-only during a Ritrascrivi > audio source missing > 'n voci da identificare'; with several conditions true exactly ONE screen Banner (the highest); superato / a failed Riassunto / the model download never produce a screen Banner
- AC-S124 STATES + render-check: both tabs, each mark, with and without a screen banner, at 1280×800 and 1024×640, light and dark, no clipped text

## Dependencies
- **ui-schede-registrazione** (OWNED here; owner schede-registrazione; projection in-process; contract_test `consumer-driven`)
  - `SorgenteRiassuntoS3 (optional presenter input)`: data class(contenuto: @Composable (RegistrazioneId) -> Unit, segno: (RegistrazioneId) -> Flow<SegnoScheda?>) — null in R1/R2 (no tabs)
- **tec-shell-ui** (consumed; owner ui-fondamenta (trascrizione-con-parlanti, merged); projection in-process; contract_test `consumer-driven`)
  - `(unchanged)`: AggiornamentiVista.cambiamenti: Flow<Cambiamento>; Cambiamento(registrazioneId: RegistrazioneId?) — as pinned in the sibling manifest
- **ui-kit-sintesi** (consumed; owner stile-sintesi; projection in-process; contract_test `consumer-driven`)
  - `FonteChip`: @Composable fun FonteChip(voceId: Int, nome: String?, inizioMs: Long, modifier: Modifier = Modifier) — not interactive
  - `GruppoFonti`: @Composable fun GruppoFonti(fonti: List<FonteChipDati>) — FlowRow, space2 gaps; FonteChipDati(voceId: Int, nome: String?, inizioMs: Long)
  - `SchedeSn`: @Composable fun SchedeSn(schede: List<String>, selezionata: Int, onSeleziona: (Int) -> Unit, segni: Map<Int, SegnoScheda> = emptyMap()); SegnoScheda = InAttesa (Clock) | InCorso (pulsing dot)
  - `ChipStato`: TipoChipStato.NonRiuscita gains an optional testo override (default 'Non riuscita')
- stile-sintesi — build dependency (merged before this block)

Sources: ux-proposal § Screen S3 (Tabs, status mark), § Banner precedence; ADR 0021 §10; related_adrs 0001, 0010, 0018, 0021; tactical-model: features/sintesi/tactical-model.md
