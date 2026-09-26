---
id: "design-system-sintesi"
type: "ui"
context: "ui"
side: "app"
wave: 1
release: "R3"
module: "features/trascrizione-con-parlanti/UI/design-system/ (README.md, anteprime/RecordingSummary.*, Tabs, FonteChip.*, StatusChip) — docs only, no code"
consumes: []
depends_on: []
related_adrs:
  - "0001"
ready_when: "Elimina registrazione (ADR 0020) merged into main and feature/sintesi rebased"
tests_nl_status: "draft"
consumes_rm: []
triggers: []
---
# design-system-sintesi — Delta del design system (solo documenti): RecordingSummary, Tabs, README Layout, FonteChip, StatusChip

## What to do
The DOCS half of the ux-proposal's 'Design-system delta' (split 2026-09-25, user answer R19-8): fold it into the sibling feature's approved design-system files once 'Elimina registrazione' is merged and feature/sintesi rebased — RecordingSummary moves to the centre-column Riassunto tab with the real content; Tabs also the centre-column selector; README § Layout sentence; new FonteChip doc + preview; StatusChip doc lists the Riassunto usage. No code (the code half is stile-sintesi).

Note: SPLIT 2026-09-25 (user answer R19-8): docs only, edits the sibling feature's design-system FILES (owner trascrizione-con-parlanti) — allowed only after the rebase. Nothing depends on it.

**ready_when:** Elimina registrazione (ADR 0020) merged into main and feature/sintesi rebased — the worker-composer does not dispatch this block before that.

## Tasks
- AC-S42 Docs: anteprime/RecordingSummary.md/.html show the centre-column Riassunto tab with Sommario, Decisioni, Azioni, Questioni aperte, Punti chiave and Fonti chips (the 'Esempio · v2' content replaced; the facts part stays marked part B); Tabs doc lists the centre-column use; README § Layout reads 'il corpo a sinistra con le schede Trascrizione / Riassunto, e il pannello destro con Voci'; new anteprime/FonteChip.md/.html; StatusChip doc lists the Riassunto usage (queued 'In coda · n' / running 'Sto riassumendo' + elapsed / failed 'Non riuscito')
- AC-S158 The docs match the code of stile-sintesi (FonteChip anatomy, SchedeSn marks, 'In coda · n' with no ordinal — the '2ª' of the ux-proposal is superseded, R19-2); no Kotlin file is touched by this block

## Dependencies
- none (owner block)

Sources: ux-proposal § Design-system delta; features/trascrizione-con-parlanti/UI/design-system/README.md; related_adrs 0001; tactical-model: features/sintesi/tactical-model.md
