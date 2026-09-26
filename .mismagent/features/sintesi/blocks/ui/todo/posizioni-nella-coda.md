---
id: "posizioni-nella-coda"
type: "port"
context: "ui"
side: "app"
wave: 1
release: "R3"
module: ":ui (snastro.ui.coda) + test fixture"
consumes:
  - "kernel-pl"
depends_on: []
related_adrs:
  - "0023"
tests_nl_status: "draft"
projection: "in-process"
pinned_types:
  "PosizioniNellaCoda (snastro.ui.coda)": "interface { fun istantanea(): PosizioniCoda }"
  PosizioniCoda: "data class(elaborazioni: Map<RegistrazioneId, Int>, riassunti: Map<RegistrazioneId, Int>) { companion VUOTA } — 1-based over ALL in_attesa items of both kinds in the global order; in_corso not counted"
contract_test: "consumer-driven"
---
# posizioni-nella-coda — Porta PosizioniNellaCoda (:ui) — posizione nella coda condivisa, calcolata dal proprietario

## What to do
Declare in :ui the port PosizioniNellaCoda.istantanea(): PosizioniCoda(elaborazioni, riassunti) keyed by RegistrazioneId, and a settable PosizioniNellaCodaFinta for presenter tests. Implemented in :avvio by the queue (avvio-coda-condivisa), the same pattern as ServizioModelli.

## Tasks
- AC-S24 PosizioniCoda.VUOTA has both maps empty; a presenter asking the position of an absent registrazioneId gets null (the row/tab then shows 'In coda' without a number)
- AC-S25 PosizioniNellaCodaFinta returns the snapshot last set and counts istantanea() calls (presenters re-read it on every Cambiamento — asserted by their own tests)

## Dependencies
- **posizioni-nella-coda** (OWNED here; owner posizioni-nella-coda; projection in-process; contract_test `consumer-driven`)
  - `PosizioniNellaCoda (snastro.ui.coda)`: interface { fun istantanea(): PosizioniCoda }
  - `PosizioniCoda`: data class(elaborazioni: Map<RegistrazioneId, Int>, riassunti: Map<RegistrazioneId, Int>) { companion VUOTA } — 1-based over ALL in_attesa items of both kinds in the global order; in_corso not counted
  - key `RegistrazioneId`: exact per kind: at most one open item per Registrazione per kind (INV-4, INV-S2)
- **kernel-pl** (consumed; owner kernel (trascrizione-con-parlanti, merged); projection in-process; contract_test `consumer-driven`)
  - `Published Language (unchanged)`: RegistrazioneId, ProgettoId, SegmentoId, VoceId, VoceRef(registrazioneId, voceId), IntervalloMs, Esito, ErroreDominio, EventoPubblicato, Creato, GeneratoreId, UnitaDiLavoro (+ testFixtures UnitaDiLavoroFinta.transazioneAperta), DispatcherEventi — as pinned in features/trascrizione-con-parlanti/building-blocks.yaml boundary kernel-pl
  - key `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; the correlation key of every Sintesi row
  - key `SegmentoId / VoceId`: minted by the trascritto aggregate per Trascritto GENERATION (ADR 0018: a replacement renumbers from 1) — stable for the generation's life; Sintesi stores them only inside a Riassunto that is deleted with its generation (INV-S8)

Sources: ADR 0023 §4; related_adrs 0002, 0012, 0023; tactical-model: features/sintesi/tactical-model.md
