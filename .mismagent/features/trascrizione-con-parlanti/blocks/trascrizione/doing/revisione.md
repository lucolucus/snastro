---
id: "revisione"
type: "application-service"
context: "trascrizione"
side: "app"
wave: 4
release: "R1"
module: ":trascrizione:applicazione (..comandi)"
consumes:
  - "kernel-pl"
  - "agg-trascritto"
  - "repo-trascrizione"
  - "eventi-revisione"
depends_on: []
related_adrs:
  - "0002"
  - "0003"
  - "0006"
  - "0007"
  - "0012"
  - "0018"
  - "0019"
commands:
  - "UnisciVoci"
  - "DividiVoce"
  - "RiassegnaSegmento"
  - "ConfermaSegmento"
---
# revisione — UnisciVoci, DividiVoce, RiassegnaSegmento

## What to do
Thin services over the trascritto root; each publishes its revisione event, whose SYNC Parlanti subscriber runs in the same transaction.

REWORK 2026-09-24 (ADR 0019): RiassegnaSegmentoServizio.esegui returns Esito<VoceId> (the destination; the new VoceId when destinazione = null) and the moved Segmento is confermato; new command ConfermaSegmento(registrazioneId, segmentoId, confermato) with ConfermaSegmentoServizio (one transaction; SegmentoConfermato once on a change, nothing on a no-op). DividiVoce unchanged here (the aggregate sets the flags). Tests AC-516, AC-517.

Note: AMENDED 2026-09-24 (ADR 0019 + Amendment 2026-09-24 (b), manifest delta 2026-09-24-semi-automatica): RiassegnaSegmento returns the destination VoceId and confirms the moved Segmento; new ConfermaSegmento (actor utente: 'Dai un nome a una frase' case (a), 'Togli conferma'), supplier of SegmentoConfermato on eventi-revisione. DividiVoce needs no service change (the aggregate sets the flags).

## Tasks
- AC-76 UnisciVoci valido pubblica VociUnite esattamente una volta con il payload fissato
- AC-77 UnisciVoci(A, A) → UnioneNonAmmessa e nessun evento
- AC-78 DividiVoce valido pubblica VoceDivisa con i segmenti spostati
- AC-79 DividiVoce con l'intera Voce → DivisioneNonAmmessa e nessun evento
- AC-80 RiassegnaSegmento valido pubblica SegmentoRiassegnato con daRimossa / aNuova corretti
- AC-81 RiassegnaSegmento verso la stessa Voce → rifiutato e nessun evento
- AC-82 Su una Registrazione senza Trascritto → TrascrittoNonTrovato
- AC-83 Se un abbonato sincrono restituisce Errore la Revisione è annullata (Trascritto invariato)
- AC-516 (ADR 0019 §5) RiassegnaSegmentoServizio.esegui returns Esito<VoceId>, the destination (the new VoceId when destinazione = null); the moved Segmento is confermato after the commit (repository round-trip through the fake); AC-80..83 unchanged
- AC-517 NEW command ConfermaSegmento(registrazioneId, segmentoId, confermato), service ConfermaSegmentoServizio: one transaction; publishes SegmentoConfermato exactly once on a change and nothing on a no-op; no Trascritto → TrascrittoNonTrovato; an unknown Segmento → SegmentoNonTrovato, nothing written

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
    - `ElaborazioneRepository`: interface { diRegistrazione(id: RegistrazioneId): List<Elaborazione>; inAttesa(): List<Elaborazione> /* FIFO by creataAlle, tie id */; inCorso(): List<Elaborazione>; trova(id: ElaborazioneId): Elaborazione?; salva(e: Elaborazione): Esito<Unit> /* Errore(ElaborazioneGiaAperta) only: another open Elaborazione of the same Registrazione while this one is open (index elaborazione_aperta_unica); several completata are allowed (ADR 0018) */; rimuoviInAttesa(id: ElaborazioneId): Esito<Unit> /* compare-and-delete (ADR 0018 Amendment (b)): deletes the row iff it exists and is still in_attesa; started → Errore(ElaborazioneGiaAvviata); absent → Errore(ElaborazioneNonTrovata); the only deletion of an Elaborazione */ }
    - `TrascrittoRepository`: interface { trova(id: RegistrazioneId): Trascritto?; conTrascritto(): List<RegistrazioneId>; salva(t: Trascritto) } — persists prossimaVoce / prossimoSegmento; salva over an existing Trascritto REPLACES it whole (Voci, Segmenti, counters: ADR 0018 replacement)
- **eventi-revisione** (consumed/implemented) — owner `eventi-pubblicati`, supplier `revisione (VociUnite, VoceDivisa, SegmentoRiassegnato, SegmentoConfermato), riassegna-segmenti (SegmentoRiassegnato, N per batch)`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `VociUnite`: data class(registrazioneId: RegistrazioneId, sopravvissuta: VoceId, rimossa: VoceId) : EventoPubblicato
    - `VoceDivisa`: data class(registrazioneId: RegistrazioneId, origine: VoceId, nuova: VoceId, segmentiSpostati: List<SegmentoId>) : EventoPubblicato
    - `SegmentoRiassegnato`: data class(registrazioneId: RegistrazioneId, segmentoId: SegmentoId, da: VoceId, a: VoceId, daRimossa: Boolean, aNuova: Boolean) : EventoPubblicato
    - `SegmentoConfermato`: data class(registrazioneId: RegistrazioneId, segmentoId: SegmentoId, confermato: Boolean) : EventoPubblicato — after commit only (view refresh); no synchronous subscriber (ADR 0019 §3)
  - keys (minting rules):
    - `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; also names audio/<id>.<ext>, cache/audio/<id>.wav and every EstrattoRef
    - `VoceId`: minted by the trascritto aggregate from its persisted counter prossimaVoce — at creation 1..n in order of FIRST APPEARANCE (smallest turn inizioMs, tie: diarizer voceIndice); DividiVoce / riassegna-to-new take prossimaVoce++; never reused, never renumbered, == the n of the label 'Voce n'; stable for the life of one Trascritto GENERATION: a Ritrascrivi replacement (ADR 0018) is a fresh Trascritto.crea numbered from 1 again, and every VoceRef-keyed Parlanti row of the old generation is purged in the same transaction (TrascrittoSostituito)
    - `SegmentoId`: minted by the trascritto aggregate at creation only, 1..m in order (inizioMs, then voceId); no Segmento is ever created afterwards (INV-8) — stable for the Trascritto generation's life (ADR 0018: a replacement renumbers from 1)
  - delivery: Parlanti revisione-policy → in-process, SYNCHRONOUS inside the publishing command's UnitaDiLavoro transaction, in emission order, exactly once per commit attempt; an Esito.Errore or exception from a sync subscriber rolls the whole command back (ADR 0012). Documento / UI refresh / Parlanti RiallineaImpronte (abbonato-riallineamento-impronte) → in-process, AFTER COMMIT only (never on rollback), at-least-once, on a background coroutine, coalesced per registrazioneId; subscribers must be idempotent (INV-23); single writer per key (one process, one DB) so no cross-stream reordering hazard. ADR 0019: a RiassegnaSegmenti commit publishes N SegmentoRiassegnato in list order — the synchronous revisione-policy runs once per event inside the one transaction (an Errore rolls the whole batch back); after-commit subscribers are coalesced per registrazioneId as today (one Rigenerazione, one RiallineaImpronte). SegmentoConfermato → after commit only

Sources: ADRs 0002, 0003, 0006, 0007, 0012, 0018, 0019 (.mismagent/decisions/); features/trascrizione-con-parlanti/tactical-model.md § Trascrizione (Commands).
