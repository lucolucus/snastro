---
id: "dialogo-elimina-riassunto"
type: "ui"
context: "ui"
side: "app"
wave: 5
release: "R3"
module: ":ui (snastro.ui.registrazioni — the S2 Elimina dialog texts)"
consumes:
  - "tec-shell-ui"
depends_on: []
related_adrs:
  - "0020"
  - "0024"
ready_when: "Elimina registrazione (ADR 0020) merged into main and feature/sintesi rebased"
tests_nl_status: "draft"
consumes_rm: []
triggers: []
---
# dialogo-elimina-riassunto — Testo del dialogo Elimina registrazione: '… il riassunto …' (ADR 0024 §3)

## What to do
Rework of the S2 row-menu dialog built by ADR 0020's delta (schermata-registrazioni, AC-626): the 'with a Trascritto' text gains 'il riassunto'. Nothing else of the dialog changes.

Note: REWORK of code built on the sibling branch (ADR 0020 delta AC-626). The sibling manifest is not edited.

**ready_when:** Elimina registrazione (ADR 0020) merged into main and feature/sintesi rebased — the worker-composer does not dispatch this block before that.

## Tasks
- AC-S141 With a Trascritto the dialog text is exactly: 'Verranno cancellati il file audio copiato nel progetto, la trascrizione con le correzioni delle voci, i nomi dati alle voci, il documento, il riassunto e le impronte vocali ricavate da questa registrazione. Non si può annullare.' — shown even when no Riassunto exists
- AC-S142 The 'no Trascritto' variant, the title, the buttons, the disabled captions and the race-backstop text are unchanged (existing AC-626..628 tests green, only the one expected string updated)

## Dependencies
- **tec-shell-ui** (consumed; owner ui-fondamenta (trascrizione-con-parlanti, merged); projection in-process; contract_test `consumer-driven`)
  - `(unchanged)`: AggiornamentiVista.cambiamenti: Flow<Cambiamento>; Cambiamento(registrazioneId: RegistrazioneId?) — as pinned in the sibling manifest

Sources: ADR 0024 §3, ux-proposal § Elimina registrazione dialog; related_adrs 0010, 0020, 0024; tactical-model: features/sintesi/tactical-model.md
