---
id: "lettore-nomi-da-parlanti-sintesi"
type: "adapter"
context: "sintesi"
side: "app"
wave: 2
release: "R3"
module: ":sintesi:adattatori (..porte)"
consumes:
  - "nomi-per-sintesi"
reuses:
  - "trascrizione-con-parlanti/kernel-pl"
related_adrs:
  - "0002"
  - "0021"
tests_nl_status: "draft"
---
# lettore-nomi-da-parlanti-sintesi — Adattatore LettoreNomiDaParlanti (su NomiDelleVoci)

## What to do
Implement Sintesi's LettoreNomi over NomiDelleVoci.nomi; pass LettoreNomiContratto real-on-real (Parlanti seeded through its commands).

## Tasks
- AC-S53 LettoreNomiContratto passes against LettoreNomiDaParlanti on databaseInMemoria(), seeded through ConfermaAttribuzione / RinominaParlante / EliminaParlante
- AC-S54 No ParlanteId leaves the adapter: the port signature returns Map<VoceRef, String> only (by-construction, not counted)

## Dependencies
- **nomi-per-sintesi** (consumed; owner lettore-nomi-sintesi; projection in-process; contract_test `consumer-driven`)
  - `LettoreNomi (snastro.sintesi.applicazione.porte)`: interface { fun nomi(r: RegistrazioneId): Map<VoceRef, String> } — attributed Voci only, current Nome, an eliminato still resolves; read at run time and display, NEVER stored (INV-S5)
  - key `VoceRef`: kernel composite (registrazioneId, voceId) — see kernel-pl
- **kernel-pl** (REUSED — boundary `kernel-pl` of features/trascrizione-con-parlanti/building-blocks.yaml, owner `kernel` already integrated on main; not redeclared in this manifest; projection in-process; contract_test `consumer-driven`)
  - `Published Language (unchanged)`: RegistrazioneId, ProgettoId, SegmentoId, VoceId, VoceRef(registrazioneId, voceId), IntervalloMs, Esito, ErroreDominio, EventoPubblicato, Creato, GeneratoreId, UnitaDiLavoro (+ testFixtures UnitaDiLavoroFinta.transazioneAperta), DispatcherEventi — as pinned in features/trascrizione-con-parlanti/building-blocks.yaml boundary kernel-pl
  - key `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; the correlation key of every Sintesi row
  - key `SegmentoId / VoceId`: minted by the trascritto aggregate per Trascritto GENERATION (ADR 0018: a replacement renumbers from 1) — stable for the generation's life; Sintesi stores them only inside a Riassunto that is deleted with its generation (INV-S8)

Sources: ADR 0021 §3; related_adrs 0002, 0012, 0021; tactical-model: features/sintesi/tactical-model.md
