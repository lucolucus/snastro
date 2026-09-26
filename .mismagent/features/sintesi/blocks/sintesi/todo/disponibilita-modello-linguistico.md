---
id: "disponibilita-modello-linguistico"
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
  - "0021"
  - "0025"
tests_nl_status: "draft"
projection: "in-process"
pinned_types:
  DisponibilitaModelloLinguistico: "interface { fun stato(): StatoModelloLinguistico }"
  StatoModelloLinguistico: "sealed { NonInstallato(dimensioneByte: Long); InDownload(scaricatiByte: Long, totaliByte: Long); DownloadFallito(motivo: MotivoDownload); Installato }"
  MotivoDownload: "enum { ConnessioneInterrotta, FileNonIntegro, SpazioInsufficiente, ScritturaFallita }"
contract_test: "consumer-driven"
---
# disponibilita-modello-linguistico — Porta DisponibilitaModelloLinguistico (+ Contratto + Finta)

## What to do
Declare the consumer-owned read port over the optional LLM model's availability: stato(): StatoModelloLinguistico (NonInstallato | InDownload | DownloadFallito(MotivoDownload) | Installato), the abstract DisponibilitaModelloLinguisticoContratto (driven by a factory that can move the supplier through its states) and a settable Finta. The download itself is never reachable from Sintesi.

## Tasks
- AC-S17 DisponibilitaModelloLinguisticoContratto: a fresh supplier reads NonInstallato(dimensioneByte > 0); a started download reads InDownload(scaricatiByte, totaliByte) with 0 ≤ scaricati ≤ totali and non-decreasing scaricati; success reads Installato; a failure reads DownloadFallito(motivo) with the mapped MotivoDownload
- AC-S18 DisponibilitaModelloLinguisticoContratto: Installato only when the installed marker matches the catalogue hash; after a supplier restart a partial download reads NonInstallato
- AC-S19 DisponibilitaModelloLinguisticoFinta passes the contract; :sintesi:* has no collaborator able to START a download (the port has no such method — by-construction, not counted)

## Dependencies
- **disponibilita-modello** (OWNED here; owner disponibilita-modello-linguistico; projection in-process; contract_test `consumer-driven`)
  - `DisponibilitaModelloLinguistico`: interface { fun stato(): StatoModelloLinguistico }
  - `StatoModelloLinguistico`: sealed { NonInstallato(dimensioneByte: Long); InDownload(scaricatiByte: Long, totaliByte: Long); DownloadFallito(motivo: MotivoDownload); Installato }
  - `MotivoDownload`: enum { ConnessioneInterrotta, FileNonIntegro, SpazioInsufficiente, ScritturaFallita }
- **kernel-pl** (consumed; owner kernel (trascrizione-con-parlanti, merged); projection in-process; contract_test `consumer-driven`)
  - `Published Language (unchanged)`: RegistrazioneId, ProgettoId, SegmentoId, VoceId, VoceRef(registrazioneId, voceId), IntervalloMs, Esito, ErroreDominio, EventoPubblicato, Creato, GeneratoreId, UnitaDiLavoro (+ testFixtures UnitaDiLavoroFinta.transazioneAperta), DispatcherEventi — as pinned in features/trascrizione-con-parlanti/building-blocks.yaml boundary kernel-pl
  - key `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; the correlation key of every Sintesi row
  - key `SegmentoId / VoceId`: minted by the trascritto aggregate per Trascritto GENERATION (ADR 0018: a replacement renumbers from 1) — stable for the generation's life; Sintesi stores them only inside a Riassunto that is deleted with its generation (INV-S8)

Sources: ADR 0021 §3 (:modelli → Sintesi read), ADR 0025 §4; related_adrs 0002, 0012, 0021, 0025; tactical-model: features/sintesi/tactical-model.md
