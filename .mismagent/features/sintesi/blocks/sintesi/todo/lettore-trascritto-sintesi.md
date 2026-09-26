---
id: "lettore-trascritto-sintesi"
type: "port"
context: "sintesi"
side: "app"
wave: 1
release: "R3"
module: ":sintesi:applicazione (..porte) + testFixtures"
consumes:
  - "kernel-pl"
depends_on: []
related_adrs:
  - "0002"
  - "0021"
tests_nl_status: "draft"
projection: "in-process"
pinned_types:
  "LettoreTrascritto (snastro.sintesi.applicazione.porte)": "interface { fun segmenti(r: RegistrazioneId): List<SegmentoSintesi>? /* null = no Trascritto */; fun elaborazioneAperta(r: RegistrazioneId): Boolean /* latest Elaborazione in_attesa|in_corso */ }"
  SegmentoSintesi: "data class(segmentoId: SegmentoId, voceId: VoceId, intervallo: IntervalloMs, testo: String) — in VociDelTrascritto.segmenti order (INV-7), current voceId after any Revisione"
contract_test: "consumer-driven"
---
# lettore-trascritto-sintesi — Porta LettoreTrascritto di Sintesi (+ Contratto + Finta)

## What to do
Declare Sintesi's own consumer-owned LettoreTrascritto (same name as Documento's, another package) with SegmentoSintesi, plus the abstract LettoreTrascrittoContratto (factory seeding the supplier state) and LettoreTrascrittoFinta that passes it.

## Tasks
- AC-S4 LettoreTrascrittoContratto: segmenti(r) is null when r has no Trascritto (never transcribed, first run in_attesa / in_corso, first run fallita); non-null list in the supplier order otherwise
- AC-S5 LettoreTrascrittoContratto: after a Revisione each SegmentoSintesi carries its CURRENT voceId with unchanged segmentoId, intervallo and testo
- AC-S6 LettoreTrascrittoContratto: elaborazioneAperta(r) is true while the latest Elaborazione is in_attesa or in_corso and false after completata / fallita / annullata; during a queued re-run segmenti(r) still returns the OLD Trascritto
- AC-S7 LettoreTrascrittoFinta passes LettoreTrascrittoContratto in the gate (D1)

## Dependencies
- **trascritto-per-sintesi** (OWNED here; owner lettore-trascritto-sintesi; projection in-process; contract_test `consumer-driven`)
  - `LettoreTrascritto (snastro.sintesi.applicazione.porte)`: interface { fun segmenti(r: RegistrazioneId): List<SegmentoSintesi>? /* null = no Trascritto */; fun elaborazioneAperta(r: RegistrazioneId): Boolean /* latest Elaborazione in_attesa|in_corso */ }
  - `SegmentoSintesi`: data class(segmentoId: SegmentoId, voceId: VoceId, intervallo: IntervalloMs, testo: String) — in VociDelTrascritto.segmenti order (INV-7), current voceId after any Revisione
  - key `SegmentoId / VoceId`: see kernel-pl — per Trascritto generation
- **kernel-pl** (consumed; owner kernel (trascrizione-con-parlanti, merged); projection in-process; contract_test `consumer-driven`)
  - `Published Language (unchanged)`: RegistrazioneId, ProgettoId, SegmentoId, VoceId, VoceRef(registrazioneId, voceId), IntervalloMs, Esito, ErroreDominio, EventoPubblicato, Creato, GeneratoreId, UnitaDiLavoro (+ testFixtures UnitaDiLavoroFinta.transazioneAperta), DispatcherEventi — as pinned in features/trascrizione-con-parlanti/building-blocks.yaml boundary kernel-pl
  - key `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; the correlation key of every Sintesi row
  - key `SegmentoId / VoceId`: minted by the trascritto aggregate per Trascritto GENERATION (ADR 0018: a replacement renumbers from 1) — stable for the generation's life; Sintesi stores them only inside a Riassunto that is deleted with its generation (INV-S8)

Sources: ADR 0021 §3 (Trascrizione → Sintesi read), architecture-overview § Boundaries; related_adrs 0002, 0012, 0021; tactical-model: features/sintesi/tactical-model.md
