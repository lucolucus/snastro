---
id: "esegui-elaborazione"
type: "application-service"
context: "trascrizione"
side: "app"
wave: 4
release: "R1"
module: ":trascrizione:applicazione (..comandi)"
consumes:
  - "kernel-pl"
  - "agg-elaborazione"
  - "agg-trascritto"
  - "repo-trascrizione"
  - "registrazione-per-trascrizione"
  - "eventi-elaborazione"
  - "tec-decodifica-trascrizione"
  - "tec-diarizzatore"
  - "tec-allineatore"
  - "tec-segnalatore-fase"
depends_on: []
related_adrs:
  - "0002"
  - "0003"
  - "0004"
  - "0005"
  - "0006"
  - "0007"
  - "0011"
  - "0012"
  - "0014"
  - "0015"
  - "0018"
  - "0019"
  - "0020"
commands:
  - "EseguiProssimaElaborazione"
  - "RecuperaElaborazioniInterrotte"
invariants:
  - "INV-5 (REWORDED 2026-09-24, ADR 0018) a Trascritto exists iff the Registrazione has ≥ 1 completata; written (created or REPLACED whole) atomically with EACH transition to completata; never touched by any other outcome"
---
# esegui-elaborazione — EseguiProssimaElaborazione + RecuperaElaborazioniInterrotte (pipeline)

## What to do
Internal commands (actor: sistema, R17). EseguiProssimaElaborazione takes the oldest in_attesa, marks it in_corso, runs decodifica → diarizzazione → trascrizione/allineamento through the ports OUTSIDE any transaction, reporting each FaseElaborazione, then commits completata + Trascritto in one short transaction, or fallita(motivo). RecuperaElaborazioniInterrotte (startup) turns every in_corso without a live run into fallita('interrotta').

REWORK 2026-09-24 (ADR 0014): the pipeline calls diarizzatore.diarizza(campioni, elaborazione.numeroPersone) instead of diarizza(campioni) — the value of the Elaborazione being run, also after a restart. New test AC-370.

REWORK 2026-09-24 (ADR 0015): add two pipeline tests with an AllineatoreFinto — an empty SegmentoGrezzo list (all turns dropped by the Allineatore's rules 2/7) → fallita 'nessun parlato rilevato' and no Trascritto (AC-386, probably already true via Trascritto.crea → NessunParlatoRilevato; prove it); two overlapping SegmentoGrezzo of different voceIndice → both Segmenti in the Trascritto with their intervals untouched (AC-387). No port change.

REWORK 2026-09-24 (ADR 0018): a completion over an existing Trascritto replaces it whole in the same transaction and publishes TrascrittoSostituito BEFORE ElaborazioneCompletata; a first completion is unchanged; any failure leaves the old Trascritto untouched; a row removed by AnnullaElaborazione is never claimed. New tests AC-437..AC-441, AC-469.

Note: The ADR 0011 NFR AC lives on benchmark-elaborazione (R17), not here. AMENDED 2026-09-24 (ADR 0014): diarizza(campioni, elaborazione.numeroPersone). AMENDED 2026-09-24 (ADR 0015): the Allineatore may legitimately return an empty list (rules 2/7) → the pipeline fails with 'nessun parlato rilevato' (AC-386); overlapping SegmentoGrezzo of different voices pass through untouched (AC-387). AMENDED 2026-09-24 (ADR 0018 + Amendment 2026-09-24 (b), manifest delta 2026-09-24-ritrascrivi): the completion transaction re-reads the existing Trascritto inside it and, if one exists, replaces it whole via TrascrittoRepository.salva and publishes TrascrittoSostituito BEFORE ElaborazioneCompletata (its synchronous Parlanti subscriber purges before COMMIT); the MOTIVO_ELABORAZIONE_GIA_COMPLETATA branch goes with the ErroreTrascrizione sweep (owned by elaborazione). The claim keeps reading the head inside its transaction (AC-314), which is what makes AnnullaElaborazione safe (AC-469).

### Invariants owned here (one test each, name starts with the tag)
- INV-5 (REWORDED 2026-09-24, ADR 0018) a Trascritto exists iff the Registrazione has ≥ 1 completata; written (created or REPLACED whole) atomically with EACH transition to completata; never touched by any other outcome

## Tasks
- AC-68 Parte sempre l'Elaborazione in_attesa più vecchia (FIFO); senza in_attesa → Ok senza effetti
- AC-69 Le fasi sono segnalate in ordine: decodifica, diarizzazione, trascrizione, allineamento
- INV-5 in caso di successo completata e Trascritto sono salvati nella stessa transazione; se il salvataggio del Trascritto fallisce l'Elaborazione non risulta completata e nessun Trascritto esiste
- AC-70 Un errore di qualunque porta in qualunque fase → fallita con un motivo in parole semplici e nessun Trascritto
- AC-71 Le Voci del Trascritto sono numerate per prima apparizione
- AC-72 Zero parlato o zero turni → fallita con motivo 'nessun parlato rilevato'
- AC-73 Nessuna transazione è aperta mentre le porte ML/audio lavorano (verifica di interazione)
- AC-74 RecuperaElaborazioniInterrotte: un'in_corso senza esecuzione viva diventa fallita 'interrotta' ed è riprovabile
- AC-75 RecuperaElaborazioniInterrotte non tocca le Elaborazioni in_attesa, completata o fallita
- AC-370 (ex AC-NP4) La pipeline passa a Diarizzatore.diarizza esattamente il numeroPersone dell'Elaborazione eseguita (assente → assente), anche per un'Elaborazione riletta dal repository dopo un riavvio (DiarizzatoreFinta che registra l'argomento)
- AC-386 (estende AC-72, ADR 0015 regola 7) Se l'Allineatore restituisce una lista vuota (ogni turno scartato perché corto o senza testo) l'Elaborazione diventa fallita con motivo 'nessun parlato rilevato' e nessun Trascritto esiste (AllineatoreFinto che restituisce emptyList)
- AC-387 (INV-7, ADR 0015 regola 9) SegmentoGrezzo sovrapposti di Voci diverse arrivano TUTTI nel Trascritto: nessuno è tagliato, unito o scartato dalla pipeline (AllineatoreFinto con due segmenti sovrapposti di voceIndice diversi → due Segmenti con gli stessi intervalli)
- AC-437 A first completion (no Trascritto before) publishes ElaborazioneCompletata and never TrascrittoSostituito (recording dispatcher fake)
- AC-438 Completion over an existing Trascritto, in ONE transaction (fake UnitaDiLavoro records a single inTransazione): the Elaborazione becomes completata; the Trascritto is replaced whole by the new one (Voci numbered from 1 by first appearance, AC-71; counters from the new Trascritto.crea; no Voce or Segmento of the old one left); TrascrittoSostituito(r) is published strictly BEFORE ElaborazioneCompletata(r); the earlier completata row stays completata
- AC-439 The old Trascritto stays readable until then: while the pipeline is inside Diarizzatore.diarizza (fake blocked on a latch), TrascrittoRepository.trova(r) returns the old Voci and Segmenti unchanged, and no transaction is open (AC-73)
- AC-440 A re-run ending fallita publishes no TrascrittoSostituito, the old Trascritto is unchanged (equal before and after) and the previous completata stays completata — for each of: a fault in any phase; 'nessun parlato rilevato' (AC-72/AC-386); Trascritto.crea refusing its input; RecuperaElaborazioniInterrotte → 'interrotta'
- AC-441 A synchronous subscriber that answers TrascrittoSostituito with Esito.Errore rolls back the whole completion: the old Trascritto is intact, no ElaborazioneCompletata reaches after-commit subscribers, and the compensation marks the run fallita with 'salvataggio del risultato non riuscito' (existing F2 path)
- AC-469 An in_attesa removed by rimuoviInAttesa (AnnullaElaborazione) before EseguiProssimaElaborazione is never started: the next oldest in_attesa starts instead, or, if none is left, Ok without effects (AC-68); the pipeline fakes are not invoked for the removed id

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
- **agg-elaborazione** (consumed/implemented) — owner `elaborazione`, projection in-process, contract_test **invariant-test**
  - pinned types:
    - `Elaborazione.accoda`: (id: ElaborazioneId, registrazioneId, creataAlle: Instant, numeroPersone: NumeroPersone?): Creato<Elaborazione, ElaborazioneAccodata> — numeroPersone fixed at creation (may be absent), immutable (ADR 0014)
    - `Elaborazione.numeroPersone`: NumeroPersone? — read-only accessor; set only by accoda (and by the persistence reconstitution); no transition changes it
    - `NumeroPersone`: @JvmInline value class(valore: Int) in :trascrizione:dominio — 1..10 inclusive; factory NumeroPersone.di(n: Int): Esito<NumeroPersone> → Errore(NumeroPersoneFuoriIntervallo) outside 1..10 (sealed ErroreTrascrizione, ErroriTrascrizione.kt); the only way to build one (ADR 0014)
    - `Elaborazione.avvia`: (alle: Instant): Esito<ElaborazioneAvviata>
    - `Elaborazione.completa`: (): Esito<ElaborazioneCompletata>
    - `Elaborazione.fallisci`: (motivo: String): Esito<ElaborazioneFallita>
    - `Elaborazione.annulla`: (): Esito<ElaborazioneAnnullata> — Ok ONLY from in_attesa (domain event ElaborazioneAnnullata(id, registrazioneId), state unchanged: a check, not a transition — the repository then deletes the never-started row, ADR 0018 Amendment (b)); any other state → Errore(ElaborazioneGiaAvviata(id))
    - `named predicates`: aperta (in_attesa|in_corso), inAttesa, completata, fallita, terminale — never compare StatoElaborazione outside the aggregate
    - `errors (ErroreTrascrizione, ErroriTrascrizione.kt)`: ElaborazioneGiaAperta(registrazioneId); ElaborazioneGiaAvviata(elaborazioneId: ElaborazioneId); ElaborazioneNonTrovata(elaborazioneId: ElaborazioneId); NumeroPersoneFuoriIntervallo(valore) — ElaborazioneGiaCompletata is DELETED (ADR 0018)
  - keys (minting rules):
    - `ElaborazioneId`: minted by avvia-elaborazione via GeneratoreId (UUID v4) — internal, never crosses a context boundary
  - §14 gates (must stay green):
    - `! grep -rnE --include='*.kt' --exclude-dir=build '\b(elaborazioneQueries)\b' . | grep -vE '^\./(persistenza/|trascrizione/adattatori/src/[A-Za-z]+/kotlin/snastro/trascrizione/adattatori/persistenza/)' | grep -q .`
    - `! grep -rnE --include='*.kt' --exclude-dir=build 'StatoElaborazione\.' . | grep -E '^\./[^:]*/src/main/' | grep -vE '^\./trascrizione/(dominio/|adattatori/src/[A-Za-z]+/kotlin/snastro/trascrizione/adattatori/persistenza/)' | grep -q .`
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
- **eventi-elaborazione** (consumed/implemented) — owner `eventi-pubblicati`, supplier `esegui-elaborazione (Avviata/Completata/Fallita/TrascrittoSostituito), annulla-elaborazione (ElaborazioneAnnullata)`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `ElaborazioneAvviata`: data class(registrazioneId: RegistrazioneId, avviataAlle: Instant) : EventoPubblicato
    - `ElaborazioneCompletata`: data class(registrazioneId: RegistrazioneId) : EventoPubblicato
    - `ElaborazioneFallita`: data class(registrazioneId: RegistrazioneId, motivo: String) : EventoPubblicato — motivo in plain Italian
    - `TrascrittoSostituito`: data class(registrazioneId: RegistrazioneId) : EventoPubblicato — published ONLY in a completion transaction that replaced an existing Trascritto, BEFORE ElaborazioneCompletata (ADR 0018)
    - `ElaborazioneAnnullata`: data class(registrazioneId: RegistrazioneId) : EventoPubblicato — published by AnnullaElaborazione in the cancelling transaction (a never-started in_attesa row deleted), AFTER COMMIT only; no synchronous subscriber (ADR 0018 Amendment (b))
  - keys (minting rules):
    - `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; also names audio/<id>.<ext>, cache/audio/<id>.wav and every EstrattoRef
  - delivery: in-process, AFTER COMMIT only (never on rollback), at-least-once, on a background coroutine, coalesced per registrazioneId; subscribers must be idempotent (INV-23); single writer per key (one process, one DB) so no cross-stream reordering hazard. EXCEPTION (ADR 0018/0012): TrascrittoSostituito has one SYNCHRONOUS subscriber (Parlanti purge, abbonato-revisione-parlanti → sostituzione-trascritto-policy) inside the publishing transaction; its other subscribers are after commit
- **tec-decodifica-trascrizione** (consumed/implemented) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `DecodificatoreAudio`: interface { fun decodifica(id: RegistrazioneId, sorgente: RiferimentoAudio); fun tutti(id: RegistrazioneId): CampioniAudio; fun campioni(id: RegistrazioneId, intervallo: IntervalloMs): CampioniAudio } — infra faults throw (ADR 0003); campioni count = (fine-inizio)*16
- **tec-diarizzatore** (consumed/implemented) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `Diarizzatore`: interface { fun diarizza(c: CampioniAudio, numeroPersone: NumeroPersone?): List<Turno> } — numeroPersone = k → at most k distinct voceIndice (may be fewer); null → automatic clustering (ADR 0014 rules; clustering per ADR 0019 §1.2: with k above the real count it tends to split one voice, never to fail); no sherpa type crosses the port (ADR 0004)
    - `NumeroPersone`: see agg-elaborazione — :trascrizione:dominio VO, 1..10 (the pipeline passes the Elaborazione's own value)
    - `Turno`: data class(intervallo: IntervalloMs, voceIndice: Int) — voceIndice >= 0, diarizer cluster index
  - keys (minting rules):
    - `voceIndice`: minted by the Diarizzatore adapter per run — transient, NEVER persisted; the trascritto aggregate maps it to VoceId by first appearance
- **tec-allineatore** (consumed/implemented) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `Allineatore`: interface { fun allinea(campioni: CampioniAudio, turni: List<Turno>): List<SegmentoGrezzo> } — the adapter is built with RiconoscitoreParlato + Vad, so strategy A or B fits the same port
    - `SegmentoGrezzo`: data class(voceIndice: Int, intervallo: IntervalloMs, testo: String) — overlaps preserved, never trimmed/dropped (INV-7, Q-4)
    - `Turno`: see tec-diarizzatore
- **tec-segnalatore-fase** (consumed/implemented) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `SegnalatoreFase`: interface { fun fase(id: RegistrazioneId, f: FaseElaborazione); fun terminata(id: RegistrazioneId) }
    - `FaseElaborazione`: enum DECODIFICA | DIARIZZAZIONE | TRASCRIZIONE | ALLINEAMENTO (trascrizione:applicazione) — progress only, not guarded state

Sources: ADRs 0002, 0003, 0004, 0005, 0006, 0007, 0011, 0012, 0014, 0015, 0018, 0019, 0020 (.mismagent/decisions/); features/trascrizione-con-parlanti/tactical-model.md § Trascrizione (INV-5, startup policy), ADR 0004/0012, architecture.md § Pipeline.
