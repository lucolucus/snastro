---
id: "persistenza-sintesi"
type: "adapter"
context: "piattaforma"
side: "app"
wave: 1
release: "R3"
module: ":persistenza (migrations/6.sqm, Riassunto.sq, RiassuntoElemento.sq, RiassuntoFonte.sq, ImpostazioniSintesi.sq)"
consumes:
  - "kernel-pl"
depends_on: []
related_adrs:
  - "0006"
  - "0007"
  - "0020"
  - "0022"
ready_when: "Elimina registrazione (ADR 0020) merged into main and feature/sintesi rebased"
tests_nl_status: "draft"
---
# persistenza-sintesi — Migrazione 6.sqm (Sintesi) + query .sq

## What to do
Own migrations/6.sqm exactly as ADR 0022 §1 (riassunto with the cap column and canonical struttura, riassunto_elemento, riassunto_fonte, impostazioni_sintesi, the two one-line partial unique indexes, the IMMEDIATE FK to registrazione) and the four .sq files with every query the repositories need (incl. the conditional completion UPDATE ... WHERE id = :id AND stato = 'in_corso'). Schema.version 7. Forward-only: 5.sqm (ADR 0020) must already be on the branch.

**ready_when:** Elimina registrazione (ADR 0020) merged into main and feature/sintesi rebased — the worker-composer does not dispatch this block before that.

## Tasks
- AC-S36 ADR 0022 enforced_by exits 0 (exigible_from this block); Schema.version = 7
- AC-S37 Migration test (:persistenza:test, CR-13): empty DB → 7 with integrity_check / foreign_key_check clean and every new query run once; Schema.migrate from empty equals Schema.create; a version-6 DB with Registrazioni migrates keeping every existing row
- AC-S38 SQL: for one registrazione a second in_attesa, in_corso or fallito row is rejected by riassunto_non_pronto_unico; a second pronto is rejected by riassunto_pronto_unico; one pronto + one in_attesa coexist
- AC-S39 SQL CHECKs: fallito without motivo_fallimento, pronto without struttura or omessi, in_attesa with a sommario, voce_id on a 'decisione' element, a duplicate (riassunto_id, tipo, posizione, segmento_id) Fonte — each rejected
- AC-S40 Deleting a registrazione row while a riassunto row references it fails immediately (IMMEDIATE FK) and changes nothing
- AC-S41 The completion UPDATE affects 0 rows on an in_attesa / pronto / fallito / absent row and 1 on an in_corso row; inAttesa is ordered by (richiesto_alle, id); the child deletes run fonte → elemento → riassunto (no cascade)

## Dependencies
- **kernel-pl** (consumed; owner kernel (trascrizione-con-parlanti, merged); projection in-process; contract_test `consumer-driven`)
  - `Published Language (unchanged)`: RegistrazioneId, ProgettoId, SegmentoId, VoceId, VoceRef(registrazioneId, voceId), IntervalloMs, Esito, ErroreDominio, EventoPubblicato, Creato, GeneratoreId, UnitaDiLavoro (+ testFixtures UnitaDiLavoroFinta.transazioneAperta), DispatcherEventi — as pinned in features/trascrizione-con-parlanti/building-blocks.yaml boundary kernel-pl
  - key `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; the correlation key of every Sintesi row
  - key `SegmentoId / VoceId`: minted by the trascritto aggregate per Trascritto GENERATION (ADR 0018: a replacement renumbers from 1) — stable for the generation's life; Sintesi stores them only inside a Riassunto that is deleted with its generation (INV-S8)

Sources: ADR 0022 §1–5, ADR 0006 (a), ADR 0007; related_adrs 0002, 0006, 0007, 0012, 0020, 0022; tactical-model: features/sintesi/tactical-model.md
