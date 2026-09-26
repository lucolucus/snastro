---
id: "lettore-nomi-sintesi"
type: "port"
context: "sintesi"
side: "app"
wave: 1
release: "R3"
module: ":sintesi:applicazione (..porte) + testFixtures"
consumes: []
reuses:
  - "trascrizione-con-parlanti/kernel-pl"
related_adrs:
  - "0002"
  - "0021"
tests_nl_status: "draft"
projection: "in-process"
pinned_types:
  "LettoreNomi (snastro.sintesi.applicazione.porte)": "interface { fun nomi(r: RegistrazioneId): Map<VoceRef, String> } — attributed Voci only, current Nome, an eliminato still resolves; read at run time and display, NEVER stored (INV-S5)"
contract_test: "consumer-driven"
---
# lettore-nomi-sintesi — Porta LettoreNomi di Sintesi (+ Contratto + Finta)

## What to do
Declare Sintesi's own LettoreNomi (VoceRef → current Nome) with LettoreNomiContratto and LettoreNomiFinta. Names are read at run time (legend) and at display, never stored (INV-S5).

## Tasks
- AC-S8 LettoreNomiContratto: nomi(r) contains only attributed Voci of r (an unattributed Voce has no key); after RinominaParlante the new Nome is returned; an eliminato Parlante still resolves to its Nome
- AC-S9 LettoreNomiContratto: every key's registrazioneId equals r; an unknown Registrazione gives an empty map
- AC-S10 LettoreNomiFinta passes LettoreNomiContratto in the gate

## Dependencies
- **nomi-per-sintesi** (OWNED here; owner lettore-nomi-sintesi; projection in-process; contract_test `consumer-driven`)
  - `LettoreNomi (snastro.sintesi.applicazione.porte)`: interface { fun nomi(r: RegistrazioneId): Map<VoceRef, String> } — attributed Voci only, current Nome, an eliminato still resolves; read at run time and display, NEVER stored (INV-S5)
  - key `VoceRef`: kernel composite (registrazioneId, voceId) — see kernel-pl
- **kernel-pl** (REUSED — boundary `kernel-pl` of features/trascrizione-con-parlanti/building-blocks.yaml, owner `kernel` already integrated on main; not redeclared in this manifest; projection in-process; contract_test `consumer-driven`)
  - `Published Language (unchanged)`: RegistrazioneId, ProgettoId, SegmentoId, VoceId, VoceRef(registrazioneId, voceId), IntervalloMs, Esito, ErroreDominio, EventoPubblicato, Creato, GeneratoreId, UnitaDiLavoro (+ testFixtures UnitaDiLavoroFinta.transazioneAperta), DispatcherEventi — as pinned in features/trascrizione-con-parlanti/building-blocks.yaml boundary kernel-pl
  - key `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; the correlation key of every Sintesi row
  - key `SegmentoId / VoceId`: minted by the trascritto aggregate per Trascritto GENERATION (ADR 0018: a replacement renumbers from 1) — stable for the generation's life; Sintesi stores them only inside a Riassunto that is deleted with its generation (INV-S8)

Sources: ADR 0021 §3 (Parlanti → Sintesi read), tactical-model § Seam granularity (Voce); related_adrs 0002, 0012, 0021; tactical-model: features/sintesi/tactical-model.md
