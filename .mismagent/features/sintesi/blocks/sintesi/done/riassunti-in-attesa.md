---
id: "riassunti-in-attesa"
type: "read-model"
context: "sintesi"
side: "app"
wave: 4
release: "R3"
module: ":sintesi:applicazione (..letture)"
consumes:
  - "repo-sintesi"
reuses:
  - "trascrizione-con-parlanti/kernel-pl"
related_adrs:
  - "0021"
  - "0023"
tests_nl_status: "draft"
view_shape:
  RiassuntoInCoda: "{ riassuntoId: String, registrazioneId: RegistrazioneId, richiestoAlle: Instant }   // list in FIFO order"
---
# riassunti-in-attesa — Read-model RiassuntiInAttesa (elenco per la coda condivisa)

## What to do
The queue listing the :avvio shared queue reads for the Riassunto source's head and positions: RiassuntiInAttesa.elenco() in FIFO order, primitive riassuntoId.

Note: Supplier of boundary riassunti-in-coda (view_shape ≡ its pinned type).

### View shape (consumer-driven) and field sources
- `RiassuntoInCoda`: { riassuntoId: String, registrazioneId: RegistrazioneId, richiestoAlle: Instant }   // list in FIFO order
- source of `RiassuntoInCoda`: RiassuntoRepository.inAttesa() (FIFO by (richiestoAlle, id)) — id.valore, registrazioneId, richiestoAlle of agg-riassunto

## Tasks
- AC-S110 elenco() lists in_attesa Riassunti only, FIFO by (richiestoAlle, id) — a tie on the millisecond ordered by id; in_corso / pronto / fallito never appear; a removed one disappears

## Dependencies
- **riassunti-in-coda** (OWNED here; owner riassunti-in-attesa; projection in-process; contract_test `consumer-driven`)
  - `RiassuntiInAttesa (snastro.sintesi.applicazione.letture)`: class { fun elenco(): List<RiassuntoInCoda> } — FIFO (richiestoAlle, id), in_attesa only
  - `RiassuntoInCoda`: data class(riassuntoId: String, registrazioneId: RegistrazioneId, richiestoAlle: Instant)
  - `EseguiProssimoRiassunto`: data class(esclusi: Set<String> = emptySet(), primaDi: Instant? = null) — claims only if richiestoAlle < primaDi (strict: the Elaborazione wins an equal ms); RecuperaRiassuntiInterrotti()
  - key `riassuntoId`: see agg-riassunto (String value of RiassuntoId)
  - key `richiestoAlle`: see agg-riassunto
- **kernel-pl** (REUSED — boundary `kernel-pl` of features/trascrizione-con-parlanti/building-blocks.yaml, owner `kernel` already integrated on main; not redeclared in this manifest; projection in-process; contract_test `consumer-driven`)
  - `Published Language (unchanged)`: RegistrazioneId, ProgettoId, SegmentoId, VoceId, VoceRef(registrazioneId, voceId), IntervalloMs, Esito, ErroreDominio, EventoPubblicato, Creato, GeneratoreId, UnitaDiLavoro (+ testFixtures UnitaDiLavoroFinta.transazioneAperta), DispatcherEventi — as pinned in features/trascrizione-con-parlanti/building-blocks.yaml boundary kernel-pl
  - key `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; the correlation key of every Sintesi row
  - key `SegmentoId / VoceId`: minted by the trascritto aggregate per Trascritto GENERATION (ADR 0018: a replacement renumbers from 1) — stable for the generation's life; Sintesi stores them only inside a Riassunto that is deleted with its generation (INV-S8)
- **repo-sintesi** (consumed; owner porte-sintesi; projection in-process; contract_test `consumer-driven`)
  - `RiassuntoRepository`: interface { fun trova(id: RiassuntoId): Riassunto?; fun diRegistrazione(r: RegistrazioneId): List<Riassunto>; fun inAttesa(): List<Riassunto> /* FIFO (richiestoAlle, id) */; fun inCorso(): List<Riassunto>; fun salva(r: Riassunto): Esito<Unit> /* upsert root + replace children, caller's transaction; riassunto_non_pronto_unico OR riassunto_pronto_unico violation → Errore(ErroreSintesi.RiassuntoGiaAperto(registrazioneId)), nothing written; any other constraint failure → infra fault (ADR 0003) — D-0003 */; fun concludi(r: Riassunto): Esito<Boolean> /* CAS in ONE call (ADR 0022 §4 steps 1–3): re-read the row; absent or not in_corso → Ok(false), nothing written; only when r is pronto, concludi ITSELF removes the previous pronto of the same Registrazione (after the in_corso check, same call — callers never remove it first); then UPDATE … WHERE id AND stato='in_corso' + children → Ok(true) — D-0003 */; fun rimuovi(id: RiassuntoId): Esito<Unit>; fun rimuoviDiRegistrazione(r: RegistrazioneId): Esito<Int> }
  - `LunghezzaMassimaRiassuntoRepository`: interface { fun trova(p: ProgettoId): LunghezzaMassimaRiassunto /* predefinita when no row */; fun salva(l: LunghezzaMassimaRiassunto): Esito<Unit> }
  - key `RiassuntoId`: see agg-riassunto

Sources: ADR 0021 §3 (Sintesi → :avvio queue listing), ADR 0023 §1; related_adrs 0002, 0007, 0012, 0021, 0022, 0023; tactical-model: features/sintesi/tactical-model.md
