---
id: "repository-sql-sintesi"
type: "adapter"
context: "sintesi"
side: "app"
wave: 4
release: "R3"
module: ":sintesi:adattatori (..persistenza)"
consumes:
  - "repo-sintesi"
  - "kernel-pl"
depends_on:
  - "persistenza-sintesi"
related_adrs:
  - "0006"
  - "0007"
  - "0012"
  - "0021"
  - "0022"
ready_when: "Elimina registrazione (ADR 0020) merged into main and feature/sintesi rebased"
tests_nl_status: "draft"
---
# repository-sql-sintesi — RiassuntoRepositorySql + LunghezzaMassimaRiassuntoRepositorySql

## What to do
SQLDelight repositories over the 6.sqm queries only (riassunto*/impostazioniSintesi): upsert + child replace, explicit child deletes, index violation → Esito mapping, the in-transaction compare-and-set completion; both contracts pass on SQL.

**ready_when:** Elimina registrazione (ADR 0020) merged into main and feature/sintesi rebased — the worker-composer does not dispatch this block before that.

## Tasks
- AC-S111 RiassuntoRepositoryContratto and LunghezzaMassimaRiassuntoRepositoryContratto pass against the SQL implementations on databaseInMemoria()
- AC-S112 riassunto_non_pronto_unico violation → Errore(ErroreSintesi.RiassuntoGiaAperto(registrazioneId)); any other constraint failure → infra fault per ADR 0003
- AC-S113 CAS race on a real SQLite FILE with two UnitaDiLavoroSql threads on a barrier (completion vs rimuoviDiRegistrazione), repeated 50 times: never a resurrected row, never two pronto, never a pronto without its children
- AC-S114 Uses only riassunto*Queries / impostazioniSintesiQueries (ADR 0021 enforced_by clauses 2–3 green)

## Dependencies
- **repo-sintesi** (consumed; owner porte-sintesi; projection in-process; contract_test `consumer-driven`)
  - `RiassuntoRepository`: interface { fun trova(id: RiassuntoId): Riassunto?; fun diRegistrazione(r: RegistrazioneId): List<Riassunto>; fun inAttesa(): List<Riassunto> /* FIFO (richiestoAlle, id) */; fun inCorso(): List<Riassunto>; fun salva(r: Riassunto): Esito<Unit> /* upsert root + replace children, caller's transaction; open-index violation → Errore(RiassuntoGiaAperto) */; fun concludi(r: Riassunto): Esito<Boolean> /* CAS: UPDATE … WHERE id AND stato='in_corso' + children; false = no effect */; fun rimuovi(id: RiassuntoId): Esito<Unit>; fun rimuoviDiRegistrazione(r: RegistrazioneId): Esito<Int> }
  - `LunghezzaMassimaRiassuntoRepository`: interface { fun trova(p: ProgettoId): LunghezzaMassimaRiassunto /* predefinita when no row */; fun salva(l: LunghezzaMassimaRiassunto): Esito<Unit> }
  - key `RiassuntoId`: see agg-riassunto
- **kernel-pl** (consumed; owner kernel (trascrizione-con-parlanti, merged); projection in-process; contract_test `consumer-driven`)
  - `Published Language (unchanged)`: RegistrazioneId, ProgettoId, SegmentoId, VoceId, VoceRef(registrazioneId, voceId), IntervalloMs, Esito, ErroreDominio, EventoPubblicato, Creato, GeneratoreId, UnitaDiLavoro (+ testFixtures UnitaDiLavoroFinta.transazioneAperta), DispatcherEventi — as pinned in features/trascrizione-con-parlanti/building-blocks.yaml boundary kernel-pl
  - key `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; the correlation key of every Sintesi row
  - key `SegmentoId / VoceId`: minted by the trascritto aggregate per Trascritto GENERATION (ADR 0018: a replacement renumbers from 1) — stable for the generation's life; Sintesi stores them only inside a Riassunto that is deleted with its generation (INV-S8)
- persistenza-sintesi — build dependency (merged before this block)

Sources: ADR 0022 §2–4; related_adrs 0002, 0006, 0007, 0012, 0021, 0022; tactical-model: features/sintesi/tactical-model.md
