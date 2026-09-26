---
id: "modifica-lunghezza-massima-riassunto"
type: "application-service"
context: "sintesi"
side: "app"
wave: 4
release: "R3"
module: ":sintesi:applicazione (..comandi)"
consumes:
  - "agg-lunghezza-massima-riassunto"
  - "repo-sintesi"
  - "eventi-sintesi"
reuses:
  - "trascrizione-con-parlanti/kernel-pl"
related_adrs:
  - "0003"
  - "0012"
  - "0022"
tests_nl_status: "confirmed"
invariants:
  - "INV-S9 within [300, 2500]"
  - "INV-S10 changing the setting never touches a queued, running or pronto Riassunto"
commands:
  - "ModificaLunghezzaMassimaRiassunto(progettoId, parole: Int)"
---
# modifica-lunghezza-massima-riassunto — Comando ModificaLunghezzaMassimaRiassunto

## What to do
Validate and save the per-Progetto lunghezza massima through its root, publish LunghezzaMassimaRiassuntoModificata(progettoId). It never touches a Riassunto.

### Invariants owned / enforced here (one test each, name starts with the tag)
- INV-S9 within [300, 2500]
- INV-S10 changing the setting never touches a queued, running or pronto Riassunto

## Tasks
_tests_nl status: CONFIRMED by the user at the rule-5 checkpoint (2026-09-25)._

- AC-S90 ModificaLunghezzaMassimaRiassunto(progettoId, parole: Int) with 1500 on a Progetto with no row → saved (trova → 1500), LunghezzaMassimaRiassuntoModificata(progettoId) after commit; a second change to 1800 updates the same row
- AC-S91 299 and 2501 → Errore(LunghezzaMassimaFuoriIntervallo), nothing written, no event
- INV-S10 an in_attesa, an in_corso and a pronto Riassunto keep their cap (rows identical before/after) and the pronto's superato is unchanged

## Dependencies
- **kernel-pl** (REUSED — boundary `kernel-pl` of features/trascrizione-con-parlanti/building-blocks.yaml, owner `kernel` already integrated on main; not redeclared in this manifest; projection in-process; contract_test `consumer-driven`)
  - `Published Language (unchanged)`: RegistrazioneId, ProgettoId, SegmentoId, VoceId, VoceRef(registrazioneId, voceId), IntervalloMs, Esito, ErroreDominio, EventoPubblicato, Creato, GeneratoreId, UnitaDiLavoro (+ testFixtures UnitaDiLavoroFinta.transazioneAperta), DispatcherEventi — as pinned in features/trascrizione-con-parlanti/building-blocks.yaml boundary kernel-pl
  - key `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; the correlation key of every Sintesi row
  - key `SegmentoId / VoceId`: minted by the trascritto aggregate per Trascritto GENERATION (ADR 0018: a replacement renumbers from 1) — stable for the generation's life; Sintesi stores them only inside a Riassunto that is deleted with its generation (INV-S8)
- **agg-lunghezza-massima-riassunto** (consumed; owner lunghezza-massima-riassunto; projection in-process; contract_test `invariant-test`)
  - `LunghezzaMassimaParole`: @JvmInline value class(valore: Int) in :sintesi:dominio; LunghezzaMassimaParole.di(n: Int): Esito<LunghezzaMassimaParole> (the only factory); MINIMO = 300, MASSIMO = 2500, PREDEFINITA = 2000 (provisional, spikes runtime-llm-in-app / qualita-riassunto)
  - `LunghezzaMassimaRiassunto`: root(progettoId: ProgettoId, parole: LunghezzaMassimaParole); LunghezzaMassimaRiassunto.predefinita(progettoId); modifica(parole: Int): Esito<LunghezzaMassimaRiassuntoModificataDominio>
  - key `progettoId`: ProgettoId (kernel) minted by crea-progetto — one setting per Progetto; no row ⇒ PREDEFINITA
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

Sources: tactical-model § Commands (ModificaLunghezzaMassimaRiassunto), INV-S9/S10; related_adrs 0002, 0003, 0007, 0012, 0021, 0022; tactical-model: features/sintesi/tactical-model.md
