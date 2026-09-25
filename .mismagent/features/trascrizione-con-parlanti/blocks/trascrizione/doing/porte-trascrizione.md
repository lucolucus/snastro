---
id: "porte-trascrizione"
type: "port"
context: "trascrizione"
side: "app"
wave: 3
release: "R1"
module: ":trascrizione:applicazione (..porte) + testFixtures"
consumes:
  - "kernel-pl"
depends_on: []
related_adrs:
  - "0002"
  - "0003"
  - "0004"
  - "0005"
  - "0006"
  - "0007"
  - "0012"
  - "0014"
  - "0018"
  - "0019"
  - "0020"
owns_boundaries:
  repo-trascrizione:
    projection: "in-process"
    contract_test: "consumer-driven"
    pinned_types:
      ElaborazioneRepository: "interface { diRegistrazione(id: RegistrazioneId): List<Elaborazione>; inAttesa(): List<Elaborazione> /* FIFO by creataAlle, tie id */; inCorso(): List<Elaborazione>; trova(id: ElaborazioneId): Elaborazione?; salva(e: Elaborazione): Esito<Unit> /* Errore(ElaborazioneGiaAperta) only: another open Elaborazione of the same Registrazione while this one is open (index elaborazione_aperta_unica); several completata are allowed (ADR 0018) */; rimuoviInAttesa(id: ElaborazioneId): Esito<Unit> /* compare-and-delete (ADR 0018 Amendment (b)): deletes the row iff it exists and is still in_attesa; started → Errore(ElaborazioneGiaAvviata); absent → Errore(ElaborazioneNonTrovata); the only deletion of an Elaborazione (amended 2026-09-25: plus rimuoviDiRegistrazione, ADR 0020) */; rimuoviDiRegistrazione(id: RegistrazioneId) /* deletes EVERY Elaborazione of the Registrazione, any state; used only by the elimination policy after its veto (ADR 0020) */ }"
      TrascrittoRepository: "interface { trova(id: RegistrazioneId): Trascritto?; conTrascritto(): List<RegistrazioneId>; salva(t: Trascritto); rimuovi(id: RegistrazioneId) /* deletes segmento, voce and trascritto rows of the Registrazione in the caller's transaction; absent = no-op (ADR 0020) */ } — persists prossimaVoce / prossimoSegmento; salva over an existing Trascritto REPLACES it whole (Voci, Segmenti, counters: ADR 0018 replacement)"
  tec-decodifica-trascrizione:
    projection: "in-process"
    contract_test: "consumer-driven"
    pinned_types:
      DecodificatoreAudio: "interface { fun decodifica(id: RegistrazioneId, sorgente: RiferimentoAudio); fun tutti(id: RegistrazioneId): CampioniAudio; fun campioni(id: RegistrazioneId, intervallo: IntervalloMs): CampioniAudio } — infra faults throw (ADR 0003); campioni count = (fine-inizio)*16"
  tec-diarizzatore:
    projection: "in-process"
    contract_test: "consumer-driven"
    pinned_types:
      Diarizzatore: "interface { fun diarizza(c: CampioniAudio, numeroPersone: NumeroPersone?): List<Turno> } — numeroPersone = k → at most k distinct voceIndice (may be fewer); null → automatic clustering (ADR 0014 rules; clustering per ADR 0019 §1.2: with k above the real count it tends to split one voice, never to fail); no sherpa type crosses the port (ADR 0004)"
      NumeroPersone: "see agg-elaborazione — :trascrizione:dominio VO, 1..10 (the pipeline passes the Elaborazione's own value)"
      Turno: "data class(intervallo: IntervalloMs, voceIndice: Int) — voceIndice >= 0, diarizer cluster index"
  tec-riconoscitore:
    projection: "in-process"
    contract_test: "consumer-driven"
    pinned_types:
      RiconoscitoreParlato: "interface { fun riconosci(c: CampioniAudio): Riconoscimento }"
      Riconoscimento: "data class(testo: String, token: List<Token>?) — token null if the model gives no timestamps"
      Token: "data class(testo: String, intervallo: IntervalloMs) — relative to the start of the given samples"
  tec-vad:
    projection: "in-process"
    contract_test: "consumer-driven"
    pinned_types:
      Vad: "interface { fun parlato(c: CampioniAudio): List<IntervalloMs> } — ordered, non-overlapping"
  tec-allineatore:
    projection: "in-process"
    contract_test: "consumer-driven"
    pinned_types:
      Allineatore: "interface { fun allinea(campioni: CampioniAudio, turni: List<Turno>): List<SegmentoGrezzo> } — the adapter is built with RiconoscitoreParlato + Vad, so strategy A or B fits the same port"
      SegmentoGrezzo: "data class(voceIndice: Int, intervallo: IntervalloMs, testo: String) — overlaps preserved, never trimmed/dropped (INV-7, Q-4)"
      Turno: "see tec-diarizzatore"
  tec-segnalatore-fase:
    projection: "in-process"
    contract_test: "consumer-driven"
    pinned_types:
      SegnalatoreFase: "interface { fun fase(id: RegistrazioneId, f: FaseElaborazione); fun terminata(id: RegistrazioneId) }"
      FaseElaborazione: "enum DECODIFICA | DIARIZZAZIONE | TRASCRIZIONE | ALLINEAMENTO (trascrizione:applicazione) — progress only, not guarded state"
---
# porte-trascrizione — Porte della Trascrizione: repository e pipeline

## What to do
Declare ElaborazioneRepository, TrascrittoRepository, DecodificatoreAudio, Diarizzatore, RiconoscitoreParlato, Vad, Allineatore, SegnalatoreFase (+ FaseElaborazione) with Finta + Contratto each; the ElaborazioneRepositoryFinta honours INV-4 like the indexes.

REWORK 2026-09-24 (ADR 0014): Diarizzatore.diarizza gains numeroPersone: NumeroPersone?; DiarizzatoreFinta records the last numeroPersone received and never returns more than k distinct voceIndice; DiarizzatoreContratto gains the k case (AC-374). Every existing caller/fake of diarizza must be updated.

REWORK 2026-09-24 (ADR 0018): ElaborazioneRepository: salva KDoc = only ElaborazioneGiaAperta (several completata allowed); + trova(id) and rimuoviInAttesa(id) (compare-and-delete); Contratto + Finta updated (AC-29 reworded, AC-433, AC-463).

REWORK 2026-09-25 (ADR 0020): ElaborazioneRepository + rimuoviDiRegistrazione(id: RegistrazioneId) (every Elaborazione of r, any state, none of another) and TrascrittoRepository + rimuovi(id: RegistrazioneId) (absent = no-op); Contratti + Finte updated (AC-619).

Note: AMENDED 2026-09-24 (ADR 0014): Diarizzatore.diarizza gains numeroPersone: NumeroPersone?; the DiarizzatoreFinta records the argument it received (used by esegui-elaborazione AC-370) and never returns more than k distinct voceIndice. AMENDED 2026-09-24 (ADR 0018 + Amendment 2026-09-24 (b), manifest delta 2026-09-24-ritrascrivi): ElaborazioneRepository: salva KDoc re-pinned (only ElaborazioneGiaAperta; several completata allowed); + trova(id: ElaborazioneId) and rimuoviInAttesa(id) (compare-and-delete) for AnnullaElaborazione; the Finta mirrors both. AMENDED 2026-09-25 (ADR 0020, manifest delta 2026-09-25-elimina-registrazione, user decision 2026-09-25, defaults accepted): ElaborazioneRepository + rimuoviDiRegistrazione(id: RegistrazioneId) and TrascrittoRepository + rimuovi(id: RegistrazioneId) (repo-trascrizione), used only by eliminazione-registrazione-policy after its veto; Finte + Contratti updated (AC-619).

## Tasks
- AC-29 (REWORDED 2026-09-24, ADR 0018) ElaborazioneRepositoryContratto: una seconda Elaborazione aperta per la stessa Registrazione → ElaborazioneGiaAperta; inAttesa in ordine FIFO di creazione (passa contro la Finta); nessun ramo GiaCompletata
- AC-30 TrascrittoRepositoryContratto: round-trip completo incluse Voci, Segmenti, prossimaVoce e prossimoSegmento
- AC-31 DecodificatoreAudioContratto: campioni(intervallo) restituisce (fine - inizio) × 16 campioni
- AC-32 DiarizzatoreContratto: ogni Turno ha inizio < fine entro la durata e voceIndice >= 0
- AC-33 RiconoscitoreParlatoContratto: i token, se presenti, cadono dentro la durata dei campioni
- AC-34 VadContratto: intervalli ordinati, non sovrapposti, entro la durata
- AC-35 AllineatoreContratto: ogni SegmentoGrezzo ha inizio < fine entro la durata, un voceIndice presente nei turni, e le sovrapposizioni tra turni non vengono tagliate né eliminate
- AC-36 SegnalatoreFaseFinta registra la sequenza di fasi ricevute
- AC-374 (ex AC-NP7) DiarizzatoreContratto: con numeroPersone = k i Turni hanno al più k voceIndice distinti; con numeroPersone assente vale AC-32 (passa contro la DiarizzatoreFinta, che registra l'ultimo numeroPersone ricevuto)
- AC-433 ElaborazioneRepositoryContratto (fake, later SQL): two completata Elaborazioni of the same Registrazione are both saved and diRegistrazione returns both; salva of an open Elaborazione while another of the same Registrazione is open → Errore(ElaborazioneGiaAperta), store unchanged; an open one next to one or more completata → Ok. The fake honours exactly this, with no GiaCompletata branch
- AC-463 ElaborazioneRepositoryContratto: trova(id) returns the saved Elaborazione or null; rimuoviInAttesa(id) on an in_attesa → Ok and it is gone from diRegistrazione/inAttesa/trova; on in_corso/completata/fallita → Errore(ElaborazioneGiaAvviata) and the store is unchanged; on an unknown id → Errore(ElaborazioneNonTrovata)
- AC-619 ElaborazioneRepositoryContratto: rimuoviDiRegistrazione(r) removes every Elaborazione of r (any state) and none of another Registrazione. TrascrittoRepositoryContratto: rimuovi(r) → trova(r) is null, conTrascritto() no longer lists r, the other Trascritti are unchanged, and it is a no-op when absent. The Finte honour exactly this

## Dependencies
- **repo-trascrizione** (OWNED here — built before its consumers) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `ElaborazioneRepository`: interface { diRegistrazione(id: RegistrazioneId): List<Elaborazione>; inAttesa(): List<Elaborazione> /* FIFO by creataAlle, tie id */; inCorso(): List<Elaborazione>; trova(id: ElaborazioneId): Elaborazione?; salva(e: Elaborazione): Esito<Unit> /* Errore(ElaborazioneGiaAperta) only: another open Elaborazione of the same Registrazione while this one is open (index elaborazione_aperta_unica); several completata are allowed (ADR 0018) */; rimuoviInAttesa(id: ElaborazioneId): Esito<Unit> /* compare-and-delete (ADR 0018 Amendment (b)): deletes the row iff it exists and is still in_attesa; started → Errore(ElaborazioneGiaAvviata); absent → Errore(ElaborazioneNonTrovata); the only deletion of an Elaborazione (amended 2026-09-25: plus rimuoviDiRegistrazione, ADR 0020) */; rimuoviDiRegistrazione(id: RegistrazioneId) /* deletes EVERY Elaborazione of the Registrazione, any state; used only by the elimination policy after its veto (ADR 0020) */ }
    - `TrascrittoRepository`: interface { trova(id: RegistrazioneId): Trascritto?; conTrascritto(): List<RegistrazioneId>; salva(t: Trascritto); rimuovi(id: RegistrazioneId) /* deletes segmento, voce and trascritto rows of the Registrazione in the caller's transaction; absent = no-op (ADR 0020) */ } — persists prossimaVoce / prossimoSegmento; salva over an existing Trascritto REPLACES it whole (Voci, Segmenti, counters: ADR 0018 replacement)
- **tec-decodifica-trascrizione** (OWNED here — built before its consumers) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `DecodificatoreAudio`: interface { fun decodifica(id: RegistrazioneId, sorgente: RiferimentoAudio); fun tutti(id: RegistrazioneId): CampioniAudio; fun campioni(id: RegistrazioneId, intervallo: IntervalloMs): CampioniAudio } — infra faults throw (ADR 0003); campioni count = (fine-inizio)*16
- **tec-diarizzatore** (OWNED here — built before its consumers) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `Diarizzatore`: interface { fun diarizza(c: CampioniAudio, numeroPersone: NumeroPersone?): List<Turno> } — numeroPersone = k → at most k distinct voceIndice (may be fewer); null → automatic clustering (ADR 0014 rules; clustering per ADR 0019 §1.2: with k above the real count it tends to split one voice, never to fail); no sherpa type crosses the port (ADR 0004)
    - `NumeroPersone`: see agg-elaborazione — :trascrizione:dominio VO, 1..10 (the pipeline passes the Elaborazione's own value)
    - `Turno`: data class(intervallo: IntervalloMs, voceIndice: Int) — voceIndice >= 0, diarizer cluster index
  - keys (minting rules):
    - `voceIndice`: minted by the Diarizzatore adapter per run — transient, NEVER persisted; the trascritto aggregate maps it to VoceId by first appearance
- **tec-riconoscitore** (OWNED here — built before its consumers) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `RiconoscitoreParlato`: interface { fun riconosci(c: CampioniAudio): Riconoscimento }
    - `Riconoscimento`: data class(testo: String, token: List<Token>?) — token null if the model gives no timestamps
    - `Token`: data class(testo: String, intervallo: IntervalloMs) — relative to the start of the given samples
- **tec-vad** (OWNED here — built before its consumers) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `Vad`: interface { fun parlato(c: CampioniAudio): List<IntervalloMs> } — ordered, non-overlapping
- **tec-allineatore** (OWNED here — built before its consumers) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `Allineatore`: interface { fun allinea(campioni: CampioniAudio, turni: List<Turno>): List<SegmentoGrezzo> } — the adapter is built with RiconoscitoreParlato + Vad, so strategy A or B fits the same port
    - `SegmentoGrezzo`: data class(voceIndice: Int, intervallo: IntervalloMs, testo: String) — overlaps preserved, never trimmed/dropped (INV-7, Q-4)
    - `Turno`: see tec-diarizzatore
- **tec-segnalatore-fase** (OWNED here — built before its consumers) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `SegnalatoreFase`: interface { fun fase(id: RegistrazioneId, f: FaseElaborazione); fun terminata(id: RegistrazioneId) }
    - `FaseElaborazione`: enum DECODIFICA | DIARIZZAZIONE | TRASCRIZIONE | ALLINEAMENTO (trascrizione:applicazione) — progress only, not guarded state
- **kernel-pl** (consumed/implemented) — owner `kernel`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `ProgettoId`: @JvmInline value class(valore: String) — UUID
    - `RegistrazioneId`: @JvmInline value class(valore: String) — UUID
    - `ElaborazioneId`: @JvmInline value class(valore: String) — UUID
    - `ParlanteId`: @JvmInline value class(valore: String) — UUID
    - `VoceId`: @JvmInline value class(numero: Int) — equals the n of 'Voce n'
    - `SegmentoId`: @JvmInline value class(numero: Int)
    - `VoceRef`: data class(registrazioneId: RegistrazioneId, voceId: VoceId)
    - `IntervalloMs`: data class(inizioMs: Long, fineMs: Long) — require 0 <= inizioMs < fineMs; ordered numerically
    - `RiferimentoAudio`: @JvmInline value class(percorsoRelativo: String)
    - `CampioniAudio`: class(campioni: FloatArray) — 16 kHz mono float, explicit equals/hashCode (CR-5)
    - `EstrattoRef`: data class(registrazioneId: RegistrazioneId, intervalli: List<IntervalloMs>) — non-empty, ordered by inizioMs, total duration <= 10 000 ms, played as a sequence
    - `Esito`: sealed interface Esito<out T> { Ok<T>(valore: T); Errore(errore: ErroreDominio) } + poi / mappa / seErrore
    - `ErroreDominio`: interface (NOT sealed — Kotlin forbids cross-module sealed subtypes; NOT Throwable); each context declares its own sealed hierarchy Errore<Contesto> : ErroreDominio in file Errori<Contesto>.kt (ADR 0003 amended)
    - `EventoDominio`: marker interface for domain events (returned by aggregate methods)
    - `EventoPubblicato`: marker interface for published events (Published Language, <ctx>:applicazione.eventi)
    - `Creato`: data class Creato<A, E>(aggregato: A, evento: E)
    - `GeneratoreId`: interface { fun nuovo(): String } — UUID v4; testFixtures GeneratoreIdFinto: 'id-1', 'id-2', …
    - `UnitaDiLavoro`: interface { fun <T> inTransazione(blocco: () -> Esito<T>): Esito<T> } — rolls back when the block returns Errore or throws
    - `DispatcherEventi`: interface { fun pubblica(evento: EventoPubblicato) } + registration of AbbonatoSincrono / AbbonatoDopoCommit
    - `RicostituzioneDaPersistenza`: @RequiresOptIn(level = ERROR) annotation class — only ..adattatori.persistenza.. opts in (CR-15)
  - keys (minting rules):
    - `ProgettoId`: minted by crea-progetto via kernel GeneratoreId (UUID v4 string) — immutable; stored in progetto.db so it survives moving/copying the project folder
    - `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; also names audio/<id>.<ext>, cache/audio/<id>.wav and every EstrattoRef
    - `VoceId`: minted by the trascritto aggregate from its persisted counter prossimaVoce — at creation 1..n in order of FIRST APPEARANCE (smallest turn inizioMs, tie: diarizer voceIndice); DividiVoce / riassegna-to-new take prossimaVoce++; never reused, never renumbered, == the n of the label 'Voce n'; stable for the life of one Trascritto GENERATION: a Ritrascrivi replacement (ADR 0018) is a fresh Trascritto.crea numbered from 1 again, and every VoceRef-keyed Parlanti row of the old generation is purged in the same transaction (TrascrittoSostituito)
    - `SegmentoId`: minted by the trascritto aggregate at creation only, 1..m in order (inizioMs, then voceId); no Segmento is ever created afterwards (INV-8) — stable for the Trascritto generation's life (ADR 0018: a replacement renumbers from 1)
    - `VoceRef`: composite (registrazioneId, voceId), typed kernel VO because >=2 contexts use it — correlation key of Attribuzione, ImprontaVocale and the Documento name map; stable as its parts
    - `ParlanteId`: minted by conferma-attribuzione (new Nome) and salta-voce via GeneratoreId (UUID v4) — stable across rinomina, promozione and eliminazione (tombstone keeps it); disappears only via INV-25 (occasionale left without Attribuzioni)
    - `RiferimentoAudio`: minted by audio-progetto (ArchivioAudio.copia): 'audio/<registrazioneId>.<source extension lowercased>', relative to the project folder — immutable

Sources: ADRs 0002, 0003, 0004, 0005, 0006, 0007, 0012, 0014, 0018, 0019, 0020 (.mismagent/decisions/); features/trascrizione-con-parlanti/architetture/architecture-overview.md (Technical ports), ADR 0004, ADR 0014.
