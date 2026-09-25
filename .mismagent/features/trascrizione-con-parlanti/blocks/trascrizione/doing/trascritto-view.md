---
id: "trascritto-view"
type: "read-model"
context: "trascrizione"
side: "app"
wave: 5
release: "R1"
module: ":trascrizione:applicazione (..letture)"
consumes:
  - "kernel-pl"
  - "agg-trascritto"
  - "repo-trascrizione"
  - "registrazione-per-trascrizione"
depends_on: []
related_adrs:
  - "0002"
  - "0003"
  - "0006"
  - "0007"
  - "0012"
  - "0018"
  - "0019"
  - "0020"
view_shape:
  registrazioneId: "RegistrazioneId"
  titolo: "String"
  dataRegistrazione: "LocalDate"
  durataMs: "Long"
  segmenti: "List<{segmentoId, voceId, inizioMs, fineMs, testo, confermato}>"
  voci: "List<{voceId, etichetta}>"
view_sources:
  registrazioneId: "← input"
  titolo: "← registrazione-per-trascrizione"
  dataRegistrazione: "← registrazione-per-trascrizione"
  durataMs: "← registrazione-per-trascrizione"
  segmenti: "← Trascritto (ordered by inizio then segmentoId across Voci); confermato ← Segmento.confermato (agg-trascritto)"
  voci: "← Trascritto; etichetta = 'Voce ' + voceId.numero"
---
# trascritto-view — TrascrittoView — fetta Trascrizione (S3)

## What to do
Trascrizione slice of S3 (R1/R8 split).

REWORK 2026-09-24 (ADR 0019): each segmenti item gains confermato as stored (view_shape). Test AC-523; AC-167 otherwise unchanged.

Note: R8: documentoPath → documento.nomeFile; audioDisponibile → LettoreAudio.disponibile; colore → ui palette(voceId). Nome/tipo/parlanteId → identificazione-voci (R1). AMENDED 2026-09-24 (ADR 0019 + Amendment 2026-09-24 (b), manifest delta 2026-09-24-semi-automatica): segmenti gain confermato (S3 pin marker, 'Togli conferma', reference-mode lines of the 'Riassegna per somiglianza' button).

### view_shape (field ← source)
- `registrazioneId`: RegistrazioneId ← ← input
- `titolo`: String ← ← registrazione-per-trascrizione
- `dataRegistrazione`: LocalDate ← ← registrazione-per-trascrizione
- `durataMs`: Long ← ← registrazione-per-trascrizione
- `segmenti`: List<{segmentoId, voceId, inizioMs, fineMs, testo, confermato}> ← ← Trascritto (ordered by inizio then segmentoId across Voci); confermato ← Segmento.confermato (agg-trascritto)
- `voci`: List<{voceId, etichetta}> ← ← Trascritto; etichetta = 'Voce ' + voceId.numero

## Tasks
- AC-167 La vista espone registrazioneId, titolo, dataRegistrazione, durataMs, i segmenti (segmentoId, voceId, inizioMs, fineMs, testo) in ordine di tempo attraverso le Voci e le voci con etichetta 'Voce n'
- AC-168 Registrazione senza Trascritto → null
- AC-523 (ADR 0019) The view exposes confermato per Segmento as stored; AC-167 otherwise unchanged

## Dependencies
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
- **agg-trascritto** (consumed/implemented) — owner `trascritto`, projection in-process, contract_test **invariant-test**
  - pinned types:
    - `Trascritto.crea`: (registrazioneId, durataMs: Long, segmenti: List<SegmentoIniziale>): Esito<Creato<Trascritto, TrascrittoCreato>> — Errore(NessunParlatoRilevato) on empty input
    - `SegmentoIniziale`: data class(voceIndice: Int, intervallo: IntervalloMs, testo: String) — trascrizione:dominio input VO
    - `Trascritto.unisci`: (sopravvive: VoceId, rimossa: VoceId): Esito<VociUnite> — keeps every confermato flag
    - `Trascritto.dividi`: (origine: VoceId, segmenti: Set<SegmentoId>): Esito<VoceDivisa> — sets confermato = true on every Segmento of S (INV-26, ADR 0019)
    - `Trascritto.riassegna`: (segmento: SegmentoId, destinazione: VoceId?): Esito<SegmentoRiassegnato> — null = a NEW Voce; the event carries a (the destination); sets confermato = true on the moved Segmento (INV-26, ADR 0019)
    - `Trascritto.riassegnaInBlocco`: (spostamenti: List<SpostamentoSegmento>): Esito<List<SegmentoRiassegnato>> — every entry validated against the PRE-batch state, moves applied in list order, then the Voci empty at the END of the batch removed (INV-6; a Voce emptied and refilled within the batch is kept); never creates a Voce, never changes a flag (INV-26); any stale entry (Segmento missing / not on da / interval differs / a missing / Segmento confermato) → Errore(TrascrittoCambiato(registrazioneId)); a == da or a duplicated segmentoId → Errore(RiassegnazioneNonAmmessa); a refusal leaves the state unchanged; empty list → Ok(emptyList()); events one per move in list order, aNuova = false, daRimossa = true on the LAST move out of each Voce empty at the end (ADR 0019 §4.5 + Amendment (b).1)
    - `SpostamentoSegmento`: data class(segmentoId: SegmentoId, da: VoceId, a: VoceId, intervallo: IntervalloMs) — trascrizione:dominio input VO; intervallo = the Segmento's interval when planned (stale guard, also against a Ritrascrivi generation swap)
    - `Trascritto.confermaSegmento`: (segmento: SegmentoId, confermato: Boolean): Esito<SegmentoConfermato?> — the same value → Ok(null), no change; unknown segment → Errore(SegmentoNonTrovato) (INV-26)
    - `Segmento.confermato`: Boolean, read-only — true iff an explicit user act placed or confirmed the Segmento on its current Voce and no ConfermaSegmento(false) revoked it (INV-26); crea starts every flag at false
    - `errors (ErroreTrascrizione, ErroriTrascrizione.kt)`: + TrascrittoCambiato(registrazioneId: RegistrazioneId) — ONE sweep owned by the trascritto rework, :ui MessaggiErrore included (ADR 0019 §4.5)
    - `read accessors`: voci: List<Voce>, segmenti: List<Segmento> (read-only copies)
  - keys (minting rules):
    - `VoceId`: minted by the trascritto aggregate from its persisted counter prossimaVoce — at creation 1..n in order of FIRST APPEARANCE (smallest turn inizioMs, tie: diarizer voceIndice); DividiVoce / riassegna-to-new take prossimaVoce++; never reused, never renumbered, == the n of the label 'Voce n'; stable for the life of one Trascritto GENERATION: a Ritrascrivi replacement (ADR 0018) is a fresh Trascritto.crea numbered from 1 again, and every VoceRef-keyed Parlanti row of the old generation is purged in the same transaction (TrascrittoSostituito)
    - `SegmentoId`: minted by the trascritto aggregate at creation only, 1..m in order (inizioMs, then voceId); no Segmento is ever created afterwards (INV-8) — stable for the Trascritto generation's life (ADR 0018: a replacement renumbers from 1)
  - §14 gates (must stay green):
    - `! grep -rnE --include='*.kt' --exclude-dir=build '\b(trascrittoQueries|voceQueries|segmentoQueries)\b' . | grep -vE '^\./(persistenza/|trascrizione/adattatori/src/[A-Za-z]+/kotlin/snastro/trascrizione/adattatori/persistenza/)' | grep -q .`
- **repo-trascrizione** (consumed/implemented) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `ElaborazioneRepository`: interface { diRegistrazione(id: RegistrazioneId): List<Elaborazione>; inAttesa(): List<Elaborazione> /* FIFO by creataAlle, tie id */; inCorso(): List<Elaborazione>; trova(id: ElaborazioneId): Elaborazione?; salva(e: Elaborazione): Esito<Unit> /* Errore(ElaborazioneGiaAperta) only: another open Elaborazione of the same Registrazione while this one is open (index elaborazione_aperta_unica); several completata are allowed (ADR 0018) */; rimuoviInAttesa(id: ElaborazioneId): Esito<Unit> /* compare-and-delete (ADR 0018 Amendment (b)): deletes the row iff it exists and is still in_attesa; started → Errore(ElaborazioneGiaAvviata); absent → Errore(ElaborazioneNonTrovata); the only deletion of an Elaborazione (amended 2026-09-25: plus rimuoviDiRegistrazione, ADR 0020) */; rimuoviDiRegistrazione(id: RegistrazioneId) /* deletes EVERY Elaborazione of the Registrazione, any state; used only by the elimination policy after its veto (ADR 0020) */ }
    - `TrascrittoRepository`: interface { trova(id: RegistrazioneId): Trascritto?; conTrascritto(): List<RegistrazioneId>; salva(t: Trascritto); rimuovi(id: RegistrazioneId) /* deletes segmento, voce and trascritto rows of the Registrazione in the caller's transaction; absent = no-op (ADR 0020) */ } — persists prossimaVoce / prossimoSegmento; salva over an existing Trascritto REPLACES it whole (Voci, Segmenti, counters: ADR 0018 replacement)
- **registrazione-per-trascrizione** (consumed/implemented) — owner `porta-registrazione-trascrizione`, supplier `catalogo-registrazioni`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `LettoreRegistrazione`: interface { fun registrazione(id: RegistrazioneId): RegistrazioneVista? } — null if unknown
    - `RegistrazioneVista`: data class(registrazioneId: RegistrazioneId, progettoId: ProgettoId, titolo: String, riferimentoAudio: RiferimentoAudio, dataRegistrazione: LocalDate, durataMs: Long)
  - keys (minting rules):
    - `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; also names audio/<id>.<ext>, cache/audio/<id>.wav and every EstrattoRef
    - `ProgettoId`: minted by crea-progetto via kernel GeneratoreId (UUID v4 string) — immutable; stored in progetto.db so it survives moving/copying the project folder
    - `RiferimentoAudio`: minted by audio-progetto (ArchivioAudio.copia): 'audio/<registrazioneId>.<source extension lowercased>', relative to the project folder — immutable
    - `titolo`: minted by servizi-registrazione: source file name without extension; immutable

Sources: ADRs 0002, 0003, 0006, 0007, 0012, 0018, 0019, 0020 (.mismagent/decisions/); features/trascrizione-con-parlanti/UI/ux-proposal.md S3 (+ R1, R8).
