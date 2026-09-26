---
id: "eliminazione-registrazione-sintesi-policy"
type: "application-service"
context: "sintesi"
side: "app"
wave: 4
release: "R3"
module: ":sintesi:applicazione (..politiche)"
consumes:
  - "repo-sintesi"
  - "eventi-sintesi"
reuses:
  - "trascrizione-con-parlanti/kernel-pl"
related_adrs:
  - "0012"
  - "0020"
  - "0024"
tests_nl_status: "confirmed"
invariants:
  - "INV-S8 no Riassunto outlives its Registrazione: every Riassunto of r removed in the deleting transaction; never vetoes"
commands:
  - "ApplicaEliminazioneRegistrazioneSintesi(registrazioneId)"
---
# eliminazione-registrazione-sintesi-policy — Policy su RegistrazioneEliminata: elimina ogni Riassunto (senza veto)

## What to do
Synchronous, inside ADR 0020's deleting transaction: riassunti.rimuoviDiRegistrazione(r) in any state with elements and Fonti; publish RiassuntoEliminato(r) iff at least one was removed. Never vetoes. Takes a RegistrazioneId only, so it is buildable before the sibling merge (only its subscriber waits).

### Invariants owned / enforced here (one test each, name starts with the tag)
- INV-S8 no Riassunto outlives its Registrazione: every Riassunto of r removed in the deleting transaction; never vetoes

## Tasks
_tests_nl status: CONFIRMED by the user at the rule-5 checkpoint (2026-09-25)._

- AC-S98 ApplicaEliminazioneRegistrazioneSintesi(registrazioneId): r with a pronto + an in_attesa, and (separate case) a pronto + an in_corso, and (separate case) a fallito → every row of r removed with its elements and Fonti; the Riassunti of another Registrazione untouched (byte-identical)
- AC-S99 RiassuntoEliminato(r) is published iff at least one was removed; r with none → Ok, nothing published
- AC-S100 Never vetoes: an in_corso Riassunto → Ok (the deletion proceeds; the run's completion will write nothing)
- AC-S101 A repository Errore is returned unchanged so the deleting transaction rolls back

## Dependencies
- **kernel-pl** (REUSED — boundary `kernel-pl` of features/trascrizione-con-parlanti/building-blocks.yaml, owner `kernel` already integrated on main; not redeclared in this manifest; projection in-process; contract_test `consumer-driven`)
  - `Published Language (unchanged)`: RegistrazioneId, ProgettoId, SegmentoId, VoceId, VoceRef(registrazioneId, voceId), IntervalloMs, Esito, ErroreDominio, EventoPubblicato, Creato, GeneratoreId, UnitaDiLavoro (+ testFixtures UnitaDiLavoroFinta.transazioneAperta), DispatcherEventi — as pinned in features/trascrizione-con-parlanti/building-blocks.yaml boundary kernel-pl
  - key `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; the correlation key of every Sintesi row
  - key `SegmentoId / VoceId`: minted by the trascritto aggregate per Trascritto GENERATION (ADR 0018: a replacement renumbers from 1) — stable for the generation's life; Sintesi stores them only inside a Riassunto that is deleted with its generation (INV-S8)
- **repo-sintesi** (consumed; owner porte-sintesi; projection in-process; contract_test `consumer-driven`)
  - `RiassuntoRepository`: interface { fun trova(id: RiassuntoId): Riassunto?; fun diRegistrazione(r: RegistrazioneId): List<Riassunto>; fun inAttesa(): List<Riassunto> /* FIFO (richiestoAlle, id) */; fun inCorso(): List<Riassunto>; fun salva(r: Riassunto): Esito<Unit> /* upsert root + replace children, caller's transaction; open-index violation → Errore(RiassuntoGiaAperto) */; fun concludi(r: Riassunto): Esito<Boolean> /* CAS: UPDATE … WHERE id AND stato='in_corso' + children; false = no effect */; fun rimuovi(id: RiassuntoId): Esito<Unit>; fun rimuoviDiRegistrazione(r: RegistrazioneId): Esito<Int> }
  - `LunghezzaMassimaRiassuntoRepository`: interface { fun trova(p: ProgettoId): LunghezzaMassimaRiassunto /* predefinita when no row */; fun salva(l: LunghezzaMassimaRiassunto): Esito<Unit> }
  - key `RiassuntoId`: see agg-riassunto
- **eventi-sintesi** (consumed; owner porte-sintesi; projection in-process; contract_test `consumer-driven`)
  - `RiassuntoRichiesto`: data class(registrazioneId: RegistrazioneId) : EventoPubblicato
  - `RiassuntoAvviato`: data class(registrazioneId: RegistrazioneId) : EventoPubblicato
  - `RiassuntoPronto`: data class(registrazioneId: RegistrazioneId) : EventoPubblicato
  - `RiassuntoFallito`: data class(registrazioneId: RegistrazioneId, motivo: String /* MotivoFallimento canonical code */) : EventoPubblicato
  - `RiassuntoEliminato`: data class(registrazioneId: RegistrazioneId) : EventoPubblicato
  - `LunghezzaMassimaRiassuntoModificata`: data class(progettoId: ProgettoId) : EventoPubblicato
  - key `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; the correlation key of every Sintesi row
  - delivery: in-process, AFTER COMMIT only (never on rollback), at-least-once, background coroutine, coalesced per registrazioneId; NO synchronous subscriber; consumers (all in :avvio) idempotent; single writer per key (one process, one DB)

Sources: ADR 0024 §1–2, tactical-model § Policies (RegistrazioneEliminata), INV-S8; related_adrs 0002, 0007, 0012, 0020, 0021, 0022, 0024; tactical-model: features/sintesi/tactical-model.md
