---
id: "lettore-trascritto-da-trascrizione-sintesi"
type: "adapter"
context: "sintesi"
side: "app"
wave: 2
release: "R3"
module: ":sintesi:adattatori (..porte)"
consumes:
  - "trascritto-per-sintesi"
  - "kernel-pl"
depends_on: []
related_adrs:
  - "0002"
  - "0018"
  - "0021"
tests_nl_status: "draft"
---
# lettore-trascritto-da-trascrizione-sintesi — Adattatore LettoreTrascrittoDaTrascrizione (su VociDelTrascritto + StatiElaborazione)

## What to do
Implement Sintesi's LettoreTrascritto over Trascrizione's public queries VociDelTrascritto.segmenti and StatiElaborazione (never *Queries); pass LettoreTrascrittoContratto real-on-real, seeding the supplier through Trascrizione's own commands on databaseInMemoria().

## Tasks
- AC-S50 LettoreTrascrittoContratto passes against LettoreTrascrittoDaTrascrizione on databaseInMemoria(), the supplier seeded only through Trascrizione commands (AvviaElaborazione, EseguiProssimaElaborazione with fake ML, Revisione, AnnullaElaborazione)
- AC-S51 Inside the completion transaction of a re-run (ADR 0018 §2, after the new Trascritto is saved) segmenti(r) returns the NEW Trascritto — the sostituzione policy relies on it
- AC-S52 Uses no generated *Queries of another context (ADR 0021 enforced_by clause 2 green)

## Dependencies
- **trascritto-per-sintesi** (consumed; owner lettore-trascritto-sintesi; projection in-process; contract_test `consumer-driven`)
  - `LettoreTrascritto (snastro.sintesi.applicazione.porte)`: interface { fun segmenti(r: RegistrazioneId): List<SegmentoSintesi>? /* null = no Trascritto */; fun elaborazioneAperta(r: RegistrazioneId): Boolean /* latest Elaborazione in_attesa|in_corso */ }
  - `SegmentoSintesi`: data class(segmentoId: SegmentoId, voceId: VoceId, intervallo: IntervalloMs, testo: String) — in VociDelTrascritto.segmenti order (INV-7), current voceId after any Revisione
  - key `SegmentoId / VoceId`: see kernel-pl — per Trascritto generation
- **kernel-pl** (consumed; owner kernel (trascrizione-con-parlanti, merged); projection in-process; contract_test `consumer-driven`)
  - `Published Language (unchanged)`: RegistrazioneId, ProgettoId, SegmentoId, VoceId, VoceRef(registrazioneId, voceId), IntervalloMs, Esito, ErroreDominio, EventoPubblicato, Creato, GeneratoreId, UnitaDiLavoro (+ testFixtures UnitaDiLavoroFinta.transazioneAperta), DispatcherEventi — as pinned in features/trascrizione-con-parlanti/building-blocks.yaml boundary kernel-pl
  - key `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; the correlation key of every Sintesi row
  - key `SegmentoId / VoceId`: minted by the trascritto aggregate per Trascritto GENERATION (ADR 0018: a replacement renumbers from 1) — stable for the generation's life; Sintesi stores them only inside a Riassunto that is deleted with its generation (INV-S8)

Sources: ADR 0021 §3, architecture-overview § Boundaries (LettoreTrascritto row); related_adrs 0002, 0012, 0018, 0021; tactical-model: features/sintesi/tactical-model.md
