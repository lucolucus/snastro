---
id: "coda-trascrizione-delta"
type: "application-service"
context: "trascrizione"
side: "app"
wave: 1
release: "R3"
module: ":trascrizione:applicazione (..comandi, ..letture)"
consumes:
  - "kernel-pl"
depends_on: []
related_adrs:
  - "0012"
  - "0018"
  - "0023"
tests_nl_status: "confirmed"
commands:
  - "EseguiProssimaElaborazione (+ nonDopo: Instant? = null)"
---
# coda-trascrizione-delta — Rework Trascrizione per la coda condivisa: EseguiProssimaElaborazione.nonDopo + ElaborazioniInAttesa

## What to do
Rework of code owned by trascrizione-con-parlanti (ADR 0023 Consequences): EseguiProssimaElaborazione gains nonDopo: Instant? = null — the claim takes its oldest eligible head only if creataAlle ≤ nonDopo, reading the head INSIDE its BEGIN IMMEDIATE transaction; new public query ElaborazioniInAttesa in ..letture. Application layer only — no aggregate, invariant or event changes. The posizioneInCoda removal is NOT here (avvio-coda-condivisa owns that sweep).

Note: REWORK of trascrizione-con-parlanti code (esegui-elaborazione, stati-elaborazione owners). The sibling manifest is not edited; this block carries the new ACs.

## Tasks
_tests_nl status: CONFIRMED by the user at the rule-5 checkpoint (2026-09-25)._

- AC-S20 With nonDopo = null the behaviour is unchanged: the existing EseguiProssimaElaborazione tests (AC-68, AC-313 exclusion, AC-314, Ritrascrivi, NumeroPersone) stay green unmodified
- AC-S21 Head creataAlle = 10:00:00.000: nonDopo 10:00:00.000 → claimed (≤ is inclusive); nonDopo 09:59:59.999 → Nessuno, nothing written, no event, the head stays in_attesa
- AC-S22 The bound is honoured INSIDE the claim transaction: the head read before is cancelled (AnnullaElaborazione) before the claim, the next in_attesa has creataAlle > nonDopo → Nessuno (not a claim of the newer item)
- AC-S23 ElaborazioniInAttesa.elenco() returns (elaborazioneId, registrazioneId, creataAlle) of in_attesa Elaborazioni only, FIFO by (creataAlle, id); in_corso / completata / fallita rows never appear; an annullata (deleted) one disappears

## Dependencies
- **elaborazioni-in-coda** (OWNED here; owner coda-trascrizione-delta; projection in-process; contract_test `consumer-driven`)
  - `ElaborazioniInAttesa (snastro.trascrizione.applicazione.letture)`: class { fun elenco(): List<ElaborazioneInCoda> } — FIFO (creataAlle, id), in_attesa only
  - `ElaborazioneInCoda`: data class(elaborazioneId: String, registrazioneId: RegistrazioneId, creataAlle: Instant)
  - `EseguiProssimaElaborazione`: data class(esclusi: Set<ElaborazioneId> = emptySet(), nonDopo: Instant? = null) — claims its oldest eligible head only if creataAlle ≤ nonDopo, head read inside its BEGIN IMMEDIATE transaction
  - key `elaborazioneId`: ElaborazioneId.valore minted by avvia-elaborazione (UUID v4) — the queue's exclusion key for the Elaborazione source
  - key `creataAlle`: minted by AvviaElaborazione from the injected clock (epoch millis) — the source's FIFO key, orderable
- **kernel-pl** (consumed; owner kernel (trascrizione-con-parlanti, merged); projection in-process; contract_test `consumer-driven`)
  - `Published Language (unchanged)`: RegistrazioneId, ProgettoId, SegmentoId, VoceId, VoceRef(registrazioneId, voceId), IntervalloMs, Esito, ErroreDominio, EventoPubblicato, Creato, GeneratoreId, UnitaDiLavoro (+ testFixtures UnitaDiLavoroFinta.transazioneAperta), DispatcherEventi — as pinned in features/trascrizione-con-parlanti/building-blocks.yaml boundary kernel-pl
  - key `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; the correlation key of every Sintesi row
  - key `SegmentoId / VoceId`: minted by the trascritto aggregate per Trascritto GENERATION (ADR 0018: a replacement renumbers from 1) — stable for the generation's life; Sintesi stores them only inside a Riassunto that is deleted with its generation (INV-S8)

Sources: ADR 0023 §1–2 + Consequences; related_adrs 0002, 0012, 0018, 0023; tactical-model: features/sintesi/tactical-model.md
