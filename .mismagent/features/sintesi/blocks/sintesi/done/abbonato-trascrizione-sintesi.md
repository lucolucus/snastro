---
id: "abbonato-trascrizione-sintesi"
type: "adapter"
context: "sintesi"
side: "app"
wave: 5
release: "R3"
module: ":sintesi:adattatori (..eventi)"
consumes: []
reuses:
  - "trascrizione-con-parlanti/eventi-elaborazione"
related_adrs:
  - "0012"
  - "0018"
  - "0021"
tests_nl_status: "draft"
---
# abbonato-trascrizione-sintesi — Abbonato sincrono a TrascrittoSostituito (Sintesi)

## What to do
AbbonatoTrascrizioneSintesi(dispatcher, politica) registers ONE synchronous subscriber for TrascrittoSostituito → ApplicaSostituzioneTrascrittoSintesi.applica(r) inside the publishing transaction; an Errore dooms the completion.

## Tasks
- AC-S115 registra() adds exactly one synchronous subscriber for TrascrittoSostituito and none for any other event (fake DispatcherEventi records the kind)
- AC-S116 Publishing TrascrittoSostituito(r) inside a fake transaction calls the policy once with r, inside the transaction; a policy Errore makes pubblica return that Errore (the completion rolls back)

## Dependencies
- **eventi-elaborazione** (REUSED — boundary `eventi-elaborazione` of features/trascrizione-con-parlanti/building-blocks.yaml, owner `eventi-pubblicati` already integrated on main; not redeclared in this manifest; projection in-process; contract_test `consumer-driven`)
  - `TrascrittoSostituito`: data class(registrazioneId: RegistrazioneId) : EventoPubblicato — published ONLY in a completion transaction that replaced a Trascritto, BEFORE ElaborazioneCompletata; now TWO synchronous subscribers (Parlanti purge, Sintesi policy — ADR 0018 §5 amended by ADR 0021)
  - key `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; the correlation key of every Sintesi row
  - delivery: SYNCHRONOUS inside the publishing (completion) transaction for TrascrittoSostituito's subscribers; an Errore dooms the completion (ADR 0018 §6 compensates to fallita)
- sostituzione-trascritto-sintesi-policy — build dependency (merged before this block)

Sources: ADR 0021 §3/§6, ADR 0018 §5 (amended); related_adrs 0012, 0018, 0021; tactical-model: features/sintesi/tactical-model.md
