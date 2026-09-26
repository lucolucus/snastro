---
id: "modello-facoltativo-avvio"
type: "adapter"
context: "piattaforma"
side: "app"
wave: 3
release: "R3"
module: ":avvio (r1/ServizioModelliProvisioning, r3 DisponibilitaModelloLinguisticoAvvio)"
consumes:
  - "tec-modelli-facoltativo"
  - "tec-modelli-ui-facoltativo"
  - "disponibilita-modello"
reuses:
  - "trascrizione-con-parlanti/kernel-pl"
related_adrs:
  - "0008"
  - "0025"
tests_nl_status: "draft"
---
# modello-facoltativo-avvio — Stato del modello facoltativo in :avvio: ServizioModelli.scaricaFacoltativo + DisponibilitaModelloLinguistico su un solo detentore di stato

## What to do
Implement ServizioModelli.scaricaFacoltativo / statoFacoltativi on a background dispatcher and Sintesi's DisponibilitaModelloLinguistico over ONE state holder (+ ProvisioningModelli.installata(id)); map ErroreModelli → MotivoDownload / ErroreServizioModelli; the licences list includes an optional entry only once installed.

## Tasks
- AC-S72 DisponibilitaModelloLinguisticoContratto passes against the :avvio implementation with a fake ProvisioningModelli and a test catalogue holding one optional entry
- AC-S73 scaricaFacoltativo(id) runs off the caller thread; during it both statoFacoltativi[id] and DisponibilitaModelloLinguistico.stato() read InDownload with the SAME byte counts (one holder)
- AC-S74 ErroreModelli → MotivoDownload: ReteAssente / DownloadFallito → ConnessioneInterrotta; HashNonValido / ArchivioNonValido → FileNonIntegro; SpazioInsufficiente → SpazioInsufficiente; ScritturaFallita → ScritturaFallita (table test)
- AC-S75 After a restart with a partial .part the state reads NonInstallato; scaricaFacoltativo resumes it
- AC-S76 licenze() lists the optional entry (nome, ruolo, licenza, attribuzione) only when installata(id)

## Dependencies
- **tec-modelli-facoltativo** (consumed; owner modelli-provisioning-facoltativo; projection in-process; contract_test `consumer-driven`)
  - `VoceCatalogo`: + obbligatoria: Boolean = true (every other field unchanged)
  - `ProvisioningModelli`: + fun installata(id: String): Boolean; + fun scarica(id: String, progresso: (scaricati: Long, totali: Long) -> Unit): Esito<Unit>; pronti()/mancanti() range over obbligatoria entries only
  - `ErroreModelli`: + SpazioInsufficiente(richiestiByte: Long)
  - key `VoceCatalogo.id (optional LLM)`: minted by the runtime-llm-in-app spike ADR (e.g. 'llm-qwen3.5-9b-q4_k_m'); any SHA-256 change mints a new id (ADR 0008 (c)(2)); URL immutable (HF …/resolve/<commit-sha>/<file>)
- **tec-modelli-ui-facoltativo** (consumed; owner servizio-modelli-facoltativo; projection in-process; contract_test `consumer-driven`)
  - `ServizioModelli`: + fun scaricaFacoltativo(id: String); + val statoFacoltativi: StateFlow<Map<String, StatoModelloFacoltativo>> (StatoModelli unchanged: required entries only)
  - `StatoModelloFacoltativo`: sealed { NonInstallato(dimensioneByte: Long); InDownload(scaricatiByte: Long, totaliByte: Long); Errore(errore: ErroreServizioModelli); Installato }
  - `ErroreServizioModelli`: + SpazioInsufficiente(richiestiByte: Long)
  - key `id`: see tec-modelli-facoltativo
- **disponibilita-modello** (consumed; owner disponibilita-modello-linguistico; projection in-process; contract_test `consumer-driven`)
  - `DisponibilitaModelloLinguistico`: interface { fun stato(): StatoModelloLinguistico }
  - `StatoModelloLinguistico`: sealed { NonInstallato(dimensioneByte: Long); InDownload(scaricatiByte: Long, totaliByte: Long); DownloadFallito(motivo: MotivoDownload); Installato }
  - `MotivoDownload`: enum { ConnessioneInterrotta, FileNonIntegro, SpazioInsufficiente, ScritturaFallita }
- **kernel-pl** (REUSED — boundary `kernel-pl` of features/trascrizione-con-parlanti/building-blocks.yaml, owner `kernel` already integrated on main; not redeclared in this manifest; projection in-process; contract_test `consumer-driven`)
  - `Published Language (unchanged)`: RegistrazioneId, ProgettoId, SegmentoId, VoceId, VoceRef(registrazioneId, voceId), IntervalloMs, Esito, ErroreDominio, EventoPubblicato, Creato, GeneratoreId, UnitaDiLavoro (+ testFixtures UnitaDiLavoroFinta.transazioneAperta), DispatcherEventi — as pinned in features/trascrizione-con-parlanti/building-blocks.yaml boundary kernel-pl
  - key `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; the correlation key of every Sintesi row
  - key `SegmentoId / VoceId`: minted by the trascritto aggregate per Trascritto GENERATION (ADR 0018: a replacement renumbers from 1) — stable for the generation's life; Sintesi stores them only inside a Riassunto that is deleted with its generation (INV-S8)
- modelli-provisioning-facoltativo — build dependency (merged before this block)
- servizio-modelli-facoltativo — build dependency (merged before this block)
- disponibilita-modello-linguistico — build dependency (merged before this block)

Sources: ADR 0025 §4, ADR 0008 R10; related_adrs 0002, 0008, 0012, 0021, 0025; tactical-model: features/sintesi/tactical-model.md
