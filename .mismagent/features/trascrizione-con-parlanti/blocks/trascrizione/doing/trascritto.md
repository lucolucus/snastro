---
id: "trascritto"
type: "aggregate"
context: "trascrizione"
side: "app"
wave: 2
release: "R1"
module: ":trascrizione:dominio"
consumes:
  - "kernel-pl"
depends_on: []
related_adrs:
  - "0002"
  - "0003"
  - "0012"
  - "0019"
invariants:
  - "INV-6 every Segmento belongs to exactly one existing Voce of the same Trascritto; every Voce has >= 1 Segmento — a Voce left empty is removed"
  - "INV-7 within a Voce, Segmenti are ordered by inizio (ties by segmentoId); inizio < fine within the duration; overlaps allowed, no Revisione blocked by an overlap"
  - "INV-8 Revisione conserves the Segmenti: same set (ids, intervals, text) before and after, only their Voce and their confermato flag change; text immutable (REWORDED 2026-09-24, ADR 0019)"
  - "INV-9 unire(A, B): A != B, both Voci of the same Trascritto; afterwards every Segmento of B is on A and B no longer exists"
  - "INV-10 dividere(A, S): S non-empty proper subset of A's Segmenti; S becomes a NEW Voce (new voceId, next label number), the rest stays on A"
  - "INV-11 riassegnare(segmento, destinazione): an existing other Voce of the same Trascritto or a NEW Voce; the source is removed if emptied"
  - "INV-12 voceId is never reused within a Trascritto; the 'Voce n' number is fixed at creation"
  - "INV-26 a Segmento is confermato iff an explicit user act placed or confirmed it on its current Voce (manual riassegnare, the moved subset of dividere, ConfermaSegmento(true)) and no ConfermaSegmento(false) has revoked it since; unire keeps every flag; riassegnaInBlocco never moves a confermato Segmento, never creates a Voce and never changes a flag; crea starts every flag at false (ADR 0019 §3)"
invariant_fields:
  - "segmento→voce assignment"
  - "prossimaVoce"
  - "prossimoSegmento"
  - "segmento.confermato"
identity: "registrazioneId (one Trascritto per Registrazione)"
tables:
  - "trascritto"
  - "voce"
  - "segmento"
owns_boundaries:
  agg-trascritto:
    projection: "in-process"
    contract_test: "invariant-test"
    pinned_types:
      "Trascritto.crea": "(registrazioneId, durataMs: Long, segmenti: List<SegmentoIniziale>): Esito<Creato<Trascritto, TrascrittoCreato>> — Errore(NessunParlatoRilevato) on empty input"
      SegmentoIniziale: "data class(voceIndice: Int, intervallo: IntervalloMs, testo: String) — trascrizione:dominio input VO"
      "Trascritto.unisci": "(sopravvive: VoceId, rimossa: VoceId): Esito<VociUnite> — keeps every confermato flag"
      "Trascritto.dividi": "(origine: VoceId, segmenti: Set<SegmentoId>): Esito<VoceDivisa> — sets confermato = true on every Segmento of S (INV-26, ADR 0019)"
      "Trascritto.riassegna": "(segmento: SegmentoId, destinazione: VoceId?): Esito<SegmentoRiassegnato> — null = a NEW Voce; the event carries a (the destination); sets confermato = true on the moved Segmento (INV-26, ADR 0019)"
      "Trascritto.riassegnaInBlocco": "(spostamenti: List<SpostamentoSegmento>): Esito<List<SegmentoRiassegnato>> — every entry validated against the PRE-batch state, moves applied in list order, then the Voci empty at the END of the batch removed (INV-6; a Voce emptied and refilled within the batch is kept); never creates a Voce, never changes a flag (INV-26); any stale entry (Segmento missing / not on da / interval differs / a missing / Segmento confermato) → Errore(TrascrittoCambiato(registrazioneId)); a == da or a duplicated segmentoId → Errore(RiassegnazioneNonAmmessa); a refusal leaves the state unchanged; empty list → Ok(emptyList()); events one per move in list order, aNuova = false, daRimossa = true on the LAST move out of each Voce empty at the end (ADR 0019 §4.5 + Amendment (b).1)"
      SpostamentoSegmento: "data class(segmentoId: SegmentoId, da: VoceId, a: VoceId, intervallo: IntervalloMs) — trascrizione:dominio input VO; intervallo = the Segmento's interval when planned (stale guard, also against a Ritrascrivi generation swap)"
      "Trascritto.confermaSegmento": "(segmento: SegmentoId, confermato: Boolean): Esito<SegmentoConfermato?> — the same value → Ok(null), no change; unknown segment → Errore(SegmentoNonTrovato) (INV-26)"
      "Segmento.confermato": "Boolean, read-only — true iff an explicit user act placed or confirmed the Segmento on its current Voce and no ConfermaSegmento(false) revoked it (INV-26); crea starts every flag at false"
      "errors (ErroreTrascrizione, ErroriTrascrizione.kt)": "+ TrascrittoCambiato(registrazioneId: RegistrazioneId) — ONE sweep owned by the trascritto rework, :ui MessaggiErrore included (ADR 0019 §4.5)"
      "read accessors": "voci: List<Voce>, segmenti: List<Segmento> (read-only copies)"
---
# trascritto — Aggregato Trascritto (Voci, Segmenti, Revisione)

## What to do
Trascritto root: crea(registrazioneId, durataMs, segmentiIniziali) numbers Voci 1..n by FIRST APPEARANCE and Segmenti 1..m by (inizio, voce), returns Errore(NessunParlatoRilevato) on empty input; Revisione methods unisci/dividi/riassegna return VociUnite/VoceDivisa/SegmentoRiassegnato. Voce id = label number (R7).

REWORK 2026-09-24 (ADR 0019 + Amendment (b)): Segmento gains the read-only flag confermato ([INV-26]: crea → false; manual riassegna and dividi's S → true; unisci keeps; confermaSegmento(s, c) sets or revokes; riassegnaInBlocco never changes it); INV-8 reworded (only the Voce and the flag change); new riassegnaInBlocco(spostamenti) — pre-batch validation, moves in list order, Voci empty at the END of the batch removed, stale entries → TrascrittoCambiato — and confermaSegmento. OWN the ErroreTrascrizione sweep adding TrascrittoCambiato(registrazioneId) with every exhaustive when and the :ui MessaggiErrore text in ONE change. Tests INV-26 (x6), INV-8 updated, AC-511..AC-515.

Note: AMENDED 2026-09-24 (ADR 0019 + Amendment 2026-09-24 (b), manifest delta 2026-09-24-semi-automatica): Segmento.confermato ([INV-26]), INV-8 reworded, riassegnaInBlocco (pre-batch validation, emptied Voci removed at the END of the batch) and confermaSegmento; OWNS the ErroreTrascrizione sweep for TrascrittoCambiato (every exhaustive when in :trascrizione and :ui MessaggiErrore in ONE change, as ADR 0018 Amendment (b) §4).

### Invariants owned here (one test each, name starts with the tag)
- INV-6 every Segmento belongs to exactly one existing Voce of the same Trascritto; every Voce has >= 1 Segmento — a Voce left empty is removed
- INV-7 within a Voce, Segmenti are ordered by inizio (ties by segmentoId); inizio < fine within the duration; overlaps allowed, no Revisione blocked by an overlap
- INV-8 Revisione conserves the Segmenti: same set (ids, intervals, text) before and after, only their Voce and their confermato flag change; text immutable (REWORDED 2026-09-24, ADR 0019)
- INV-9 unire(A, B): A != B, both Voci of the same Trascritto; afterwards every Segmento of B is on A and B no longer exists
- INV-10 dividere(A, S): S non-empty proper subset of A's Segmenti; S becomes a NEW Voce (new voceId, next label number), the rest stays on A
- INV-11 riassegnare(segmento, destinazione): an existing other Voce of the same Trascritto or a NEW Voce; the source is removed if emptied
- INV-12 voceId is never reused within a Trascritto; the 'Voce n' number is fixed at creation
- INV-26 a Segmento is confermato iff an explicit user act placed or confirmed it on its current Voce (manual riassegnare, the moved subset of dividere, ConfermaSegmento(true)) and no ConfermaSegmento(false) has revoked it since; unire keeps every flag; riassegnaInBlocco never moves a confermato Segmento, never creates a Voce and never changes a flag; crea starts every flag at false (ADR 0019 §3)

## Tasks
- INV-6 riassegnare l'ultimo Segmento di una Voce la rimuove; unire rimuove B; nessuna Voce resta vuota
- INV-7 i Segmenti di una Voce sono ordinati per inizio con pareggio per segmentoId; Segmenti sovrapposti sono accettati e una Revisione su di essi non è mai bloccata
- INV-8 dopo unire, dividere, riassegnare, riassegnaInBlocco e confermaSegmento l'insieme (id, intervallo, testo) dei Segmenti è identico a prima (REWORDED 2026-09-24, ADR 0019: only the Voce and the confermato flag may change)
- INV-9 unire(A, A) → Errore(UnioneNonAmmessa); unire(A, B) sposta tutti i Segmenti di B su A e rimuove B
- INV-10 dividere con S vuoto o S = tutti i Segmenti di A → Errore(DivisioneNonAmmessa); con S valido nasce una Voce con il prossimo numero
- INV-11 riassegnare verso la stessa Voce o verso una Voce inesistente → Errore; verso null crea una Voce nuova
- INV-12 dopo unire e dividere il nuovo voceId è sempre maggiore di tutti quelli mai usati (anche rimossi)
- AC-20 crea numera le Voci per prima apparizione (la Voce che parla per prima è Voce 1) e i Segmenti per (inizio, voce)
- AC-21 crea con zero segmenti → Errore(NessunParlatoRilevato)
- INV-26 crea → every confermato flag is false
- INV-26 a manual riassegna → the moved Segmento is confermato
- INV-26 dividi → every Segmento of S is confermato, and the Segmenti left on A keep their flags
- INV-26 unisci → every flag is kept
- INV-26 riassegnaInBlocco never changes a flag and refuses a confermato Segmento (TrascrittoCambiato)
- INV-26 confermaSegmento(s, false) revokes the flag
- AC-511 (REWORDED 2026-09-24, batch semantics — ADR 0019 Amendment (b).1) riassegnaInBlocco on a valid list: every entry is validated against the pre-batch state and every Segmento ends on its a; one SegmentoRiassegnato per move in list order, aNuova = false, daRimossa true exactly on the LAST move out of each Voce that is empty at the end of the batch; the Voci empty at the end are removed (INV-6) and their numbers never reused (INV-12); a Voce emptied and then refilled within the list is kept (fixture [s1: V2→V3, s2: V4→V2], s1 being V2's only Segmento → Ok, V2 exists and holds s2, no daRimossa for V2); no Voce is created
- AC-512 riassegnaInBlocco refusals (table; state unchanged after each, compared by value): Segmento missing, not on da, interval differs, a missing, Segmento confermato → TrascrittoCambiato; a == da, duplicated segmentoId → RiassegnazioneNonAmmessa; one stale entry at the END of a 10-entry list → nothing applied; an entry whose a is emptied by an EARLIER entry of the same list is NOT stale (pre-batch validation)
- AC-513 confermaSegmento sets the flag and returns the event; the same value → Ok(null) with no change; an unknown Segmento → SegmentoNonTrovato
- AC-514 riassegnaInBlocco(emptyList()) → Ok(emptyList()), state unchanged
- AC-515 (sweep) ErroreTrascrizione.TrascrittoCambiato(registrazioneId) is added in ONE change together with every exhaustive when; :ui MessaggiErrore maps it to 'La trascrizione è cambiata dopo il confronto: ricalcola l'anteprima' (REWORDED, Amendment (b).2); the pipeline's motivo table and the fakes compile

## Dependencies
- **agg-trascritto** (OWNED here — built before its consumers) — owner `trascritto`, projection in-process, contract_test **invariant-test**
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

Sources: ADRs 0002, 0003, 0012, 0019 (.mismagent/decisions/); features/trascrizione-con-parlanti/tactical-model.md § Trascrizione + Seam granularity.
