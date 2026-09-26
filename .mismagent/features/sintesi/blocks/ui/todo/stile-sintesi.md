---
id: "stile-sintesi"
type: "ui"
context: "ui"
side: "app"
wave: 1
release: "R3"
module: ":ui (snastro.ui.stile)"
consumes:
  - "kernel-pl"
depends_on: []
related_adrs:
  - "0001"
  - "0002"
tests_nl_status: "draft"
consumes_rm: []
triggers: []
---
# stile-sintesi — Ui-kit per il Riassunto: FonteChip, GruppoFonti, SchedeSn, testo di ChipStato

## What to do
The CODE half of the ux-proposal's 'Design-system delta' (split 2026-09-25, user answer R19-8): the new Compose components FonteChip and GruppoFonti, the segmented SchedeSn (Tabs) with an optional per-tab mark, and a text override on ChipStato.NonRiuscita. Derived owner (rule 11) of the ui-kit pieces both wave-5 Sintesi ui blocks share. Buildable now: it needs nothing from the sibling branch. No screen code; the design-system DOCS are design-system-sintesi (gated).

Note: SPLIT 2026-09-25 (user answer R19-8): the code half of the former design-system-sintesi; keeps its AC numbers (AC-S43..S47). Ungated.

## Tasks
- AC-S43 FonteChip(voceId, nome: String?, inizioMs): dot + name, or ring dot + 'Voce n' in inkMuted when nome is null, then the timecode (65 000 → '1:05', 3 725 000 → '1:02:05'); label size, radiusPill, sunken fill, no border; NOT interactive (no clickable/hover modifier, no pointer cursor — asserted on the semantics tree: no OnClick action)
- AC-S44 GruppoFonti(fonti) lays the chips out after the element text with space2 gaps and wraps to the next line instead of clipping (render-check with 5 chips and a 40-character name at 1024×640)
- AC-S45 SchedeSn(schede, selezionata, onSeleziona, segno?) = the segmented Tabs: selected/unselected styles, keyboard focus ring (AC-570 rule), an optional trailing mark per tab (pulsing dot honouring reduce-motion, or Icona.Clock); reuses the Voci-panel implementation if one exists (moved into snastro.ui.stile, callers updated)
- AC-S46 ChipStato reuse: InCoda(n) renders 'In coda · n' (S2's wording, no ordinal — user 2026-09-25, R19-2) and InCorso('Sto riassumendo', trascorsoMs) the running state, with no new type; NonRiuscita gains an optional text override so the tab can say 'Non riuscito' while S2 keeps 'Non riuscita'
- AC-S47 Render-check: FonteChip named / unnamed / h:mm:ss, GruppoFonti wrapping, SchedeSn both states with each mark, light and dark

## Dependencies
- **ui-kit-sintesi** (OWNED here; owner stile-sintesi; projection in-process; contract_test `consumer-driven`)
  - `FonteChip`: @Composable fun FonteChip(voceId: Int, nome: String?, inizioMs: Long, modifier: Modifier = Modifier) — not interactive
  - `GruppoFonti`: @Composable fun GruppoFonti(fonti: List<FonteChipDati>) — FlowRow, space2 gaps; FonteChipDati(voceId: Int, nome: String?, inizioMs: Long)
  - `SchedeSn`: @Composable fun SchedeSn(schede: List<String>, selezionata: Int, onSeleziona: (Int) -> Unit, segni: Map<Int, SegnoScheda> = emptyMap()); SegnoScheda = InAttesa (Clock) | InCorso (pulsing dot)
  - `ChipStato`: TipoChipStato.NonRiuscita gains an optional testo override (default 'Non riuscita')
- **kernel-pl** (consumed; owner kernel (trascrizione-con-parlanti, merged); projection in-process; contract_test `consumer-driven`)
  - `Published Language (unchanged)`: RegistrazioneId, ProgettoId, SegmentoId, VoceId, VoceRef(registrazioneId, voceId), IntervalloMs, Esito, ErroreDominio, EventoPubblicato, Creato, GeneratoreId, UnitaDiLavoro (+ testFixtures UnitaDiLavoroFinta.transazioneAperta), DispatcherEventi — as pinned in features/trascrizione-con-parlanti/building-blocks.yaml boundary kernel-pl
  - key `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; the correlation key of every Sintesi row
  - key `SegmentoId / VoceId`: minted by the trascritto aggregate per Trascritto GENERATION (ADR 0018: a replacement renumbers from 1) — stable for the generation's life; Sintesi stores them only inside a Riassunto that is deleted with its generation (INV-S8)

Sources: ux-proposal § Design-system delta; features/trascrizione-con-parlanti/UI/design-system/README.md; related_adrs 0001, 0002, 0012; tactical-model: features/sintesi/tactical-model.md
