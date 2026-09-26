---
id: "abbonato-progetto-sintesi"
type: "adapter"
context: "sintesi"
side: "app"
wave: 5
release: "R3"
module: ":sintesi:adattatori (..eventi)"
consumes: []
reuses:
  - "trascrizione-con-parlanti/eventi-progetto"
related_adrs:
  - "0012"
  - "0020"
  - "0024"
ready_when: "SATISFIED 2026-09-26 — Elimina registrazione (ADR 0020) is on main (6daba4e) and integration/sintesi is rebased on it"
tests_nl_status: "draft"
---
# abbonato-progetto-sintesi — Abbonato sincrono a RegistrazioneEliminata (Sintesi)

## What to do
AbbonatoProgettoSintesi(dispatcher, politica) registers ONE synchronous subscriber for RegistrazioneEliminata → ApplicaEliminazioneRegistrazioneSintesi.applica(r) inside ADR 0020's deleting transaction (step 4, before the registrazione row is removed).

**ready_when:** SATISFIED 2026-09-26 — Elimina registrazione (ADR 0020) is on main (6daba4e) and integration/sintesi is rebased on it.

## Tasks
- AC-S117 registra() adds exactly one synchronous subscriber for RegistrazioneEliminata; publishing it inside a fake transaction calls the policy once with its registrazioneId
- AC-S118 A policy Errore is returned by pubblica unchanged (EliminaRegistrazione then fails and changes nothing)

## Dependencies
- **eventi-progetto-eliminazione** (REUSED — boundary `eventi-progetto` of features/trascrizione-con-parlanti/building-blocks.yaml, owner `eventi-pubblicati` already integrated on main; not redeclared in this manifest; projection in-process; contract_test `consumer-driven`)
  - `RegistrazioneEliminata`: data class(registrazioneId: RegistrazioneId, progettoId: ProgettoId, titolo: String, dataRegistrazione: LocalDate, riferimentoAudio: RiferimentoAudio) : EventoPubblicato (snastro.progetto.applicazione.eventi) — Sintesi reads only registrazioneId
  - key `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; the correlation key of every Sintesi row
  - delivery: SYNCHRONOUS inside EliminaRegistrazione's single transaction (ADR 0020 §2 step 4, before the registrazione row is removed); any subscriber Errore dooms the deletion; after-commit consumers separately
  - ready_when: SATISFIED 2026-09-26 — Elimina registrazione (ADR 0020) is on main (6daba4e) and integration/sintesi is rebased on it
- eliminazione-registrazione-sintesi-policy — build dependency (merged before this block)

Sources: ADR 0024 §1, ADR 0020 §2 step 4; related_adrs 0012, 0020, 0024; tactical-model: features/sintesi/tactical-model.md
