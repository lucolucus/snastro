---
id: "esegui-elaborazione"
type: "application-service"
context: "trascrizione"
side: "app"
wave: 4
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
commands:
  - "EseguiProssimaElaborazione"
  - "RecuperaElaborazioniInterrotte"
invariants:
  - "INV-5 a Trascritto exists iff its Registrazione has a completata Elaborazione; created atomically with the transition to completata"
---
# esegui-elaborazione — EseguiProssimaElaborazione + RecuperaElaborazioniInterrotte (pipeline)

## What to do
Internal commands (actor: sistema, R17). EseguiProssimaElaborazione takes the oldest in_attesa, marks it in_corso, runs decodifica → diarizzazione → trascrizione/allineamento through the ports OUTSIDE any transaction, reporting each FaseElaborazione, then commits completata + Trascritto in one short transaction, or fallita(motivo). RecuperaElaborazioniInterrotte (startup) turns every in_corso without a live run into fallita('interrotta').

Note: The ADR 0011 NFR AC lives on benchmark-elaborazione (R17), not here.

### Invariants owned here (one test each, name starts with the tag)
- INV-5 a Trascritto exists iff its Registrazione has a completata Elaborazione; created atomically with the transition to completata

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
    - `VoceId`: minted by the trascritto aggregate from its persisted counter prossimaVoce — at creation 1..n in order of FIRST APPEARANCE (smallest turn inizioMs, tie: diarizer voceIndice); DividiVoce / riassegna-to-new take prossimaVoce++; never reused, never renumbered, == the n of the label 'Voce n'; stable for the Trascritto's life (= forever: no re-run after completata)
    - `SegmentoId`: minted by the trascritto aggregate at creation only, 1..m in order (inizioMs, then voceId); no Segmento is ever created afterwards (INV-8) — stable forever
    - `VoceRef`: composite (registrazioneId, voceId), typed kernel VO because >=2 contexts use it — correlation key of Attribuzione, ImprontaVocale and the Documento name map; stable as its parts
    - `ParlanteId`: minted by conferma-attribuzione (new Nome) and salta-voce via GeneratoreId (UUID v4) — stable across rinomina, promozione and eliminazione (tombstone keeps it); disappears only via INV-25 (occasionale left without Attribuzioni)
    - `RiferimentoAudio`: minted by audio-progetto (ArchivioAudio.copia): 'audio/<registrazioneId>.<source extension lowercased>', relative to the project folder — immutable
- **agg-elaborazione** (consumed/implemented) — owner `elaborazione`, projection in-process, contract_test **invariant-test**
  - pinned types:
    - `Elaborazione.accoda`: (id: ElaborazioneId, registrazioneId, creataAlle: Instant): Creato<Elaborazione, ElaborazioneAccodata>
    - `Elaborazione.avvia`: (alle: Instant): Esito<ElaborazioneAvviata>
    - `Elaborazione.completa`: (): Esito<ElaborazioneCompletata>
    - `Elaborazione.fallisci`: (motivo: String): Esito<ElaborazioneFallita>
    - `named predicates`: aperta (in_attesa|in_corso), completata, fallita, terminale — never compare StatoElaborazione outside the aggregate
  - keys (minting rules):
    - `ElaborazioneId`: minted by avvia-elaborazione via GeneratoreId (UUID v4) — internal, never crosses a context boundary
  - §14 gates (must stay green):
    - `! grep -rnE --include='*.kt' --exclude-dir=build '\b(elaborazioneQueries)\b' . | grep -vE '^\./(persistenza/|trascrizione/adattatori/src/[A-Za-z]+/kotlin/snastro/trascrizione/adattatori/persistenza/)' | grep -q .`
    - `! grep -rnE --include='*.kt' --exclude-dir=build 'StatoElaborazione\.' . | grep -E '^\./[^:]*/src/main/' | grep -vE '^\./trascrizione/(dominio/|adattatori/src/[A-Za-z]+/kotlin/snastro/trascrizione/adattatori/persistenza/)' | grep -q .`
- **agg-trascritto** (consumed/implemented) — owner `trascritto`, projection in-process, contract_test **invariant-test**
  - pinned types:
    - `Trascritto.crea`: (registrazioneId, durataMs: Long, segmenti: List<SegmentoIniziale>): Esito<Creato<Trascritto, TrascrittoCreato>> — Errore(NessunParlatoRilevato) on empty input
    - `SegmentoIniziale`: data class(voceIndice: Int, intervallo: IntervalloMs, testo: String) — trascrizione:dominio input VO
    - `Trascritto.unisci`: (sopravvive: VoceId, rimossa: VoceId): Esito<VociUnite>
    - `Trascritto.dividi`: (origine: VoceId, segmenti: Set<SegmentoId>): Esito<VoceDivisa>
    - `Trascritto.riassegna`: (segmento: SegmentoId, destinazione: VoceId?): Esito<SegmentoRiassegnato> — null = a NEW Voce
    - `read accessors`: voci: List<Voce>, segmenti: List<Segmento> (read-only copies)
  - keys (minting rules):
    - `VoceId`: minted by the trascritto aggregate from its persisted counter prossimaVoce — at creation 1..n in order of FIRST APPEARANCE (smallest turn inizioMs, tie: diarizer voceIndice); DividiVoce / riassegna-to-new take prossimaVoce++; never reused, never renumbered, == the n of the label 'Voce n'; stable for the Trascritto's life (= forever: no re-run after completata)
    - `SegmentoId`: minted by the trascritto aggregate at creation only, 1..m in order (inizioMs, then voceId); no Segmento is ever created afterwards (INV-8) — stable forever
  - §14 gates (must stay green):
    - `! grep -rnE --include='*.kt' --exclude-dir=build '\b(trascrittoQueries|voceQueries|segmentoQueries)\b' . | grep -vE '^\./(persistenza/|trascrizione/adattatori/src/[A-Za-z]+/kotlin/snastro/trascrizione/adattatori/persistenza/)' | grep -q .`
- **repo-trascrizione** (consumed/implemented) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `ElaborazioneRepository`: interface { diRegistrazione(id: RegistrazioneId): List<Elaborazione>; inAttesa(): List<Elaborazione> /* FIFO by creataAlle, tie id */; inCorso(): List<Elaborazione>; salva(e: Elaborazione): Esito<Unit> /* Errore(ElaborazioneGiaAperta | ElaborazioneGiaCompletata) from the ADR 0007 indexes */ }
    - `TrascrittoRepository`: interface { trova(id: RegistrazioneId): Trascritto?; conTrascritto(): List<RegistrazioneId>; salva(t: Trascritto) } — persists prossimaVoce / prossimoSegmento
- **registrazione-per-trascrizione** (consumed/implemented) — owner `porta-registrazione-trascrizione`, supplier `catalogo-registrazioni`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `LettoreRegistrazione`: interface { fun registrazione(id: RegistrazioneId): RegistrazioneVista? } — null if unknown
    - `RegistrazioneVista`: data class(registrazioneId: RegistrazioneId, progettoId: ProgettoId, titolo: String, riferimentoAudio: RiferimentoAudio, dataRegistrazione: LocalDate, durataMs: Long)
  - keys (minting rules):
    - `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; also names audio/<id>.<ext>, cache/audio/<id>.wav and every EstrattoRef
    - `ProgettoId`: minted by crea-progetto via kernel GeneratoreId (UUID v4 string) — immutable; stored in progetto.db so it survives moving/copying the project folder
    - `RiferimentoAudio`: minted by audio-progetto (ArchivioAudio.copia): 'audio/<registrazioneId>.<source extension lowercased>', relative to the project folder — immutable
    - `titolo`: minted by servizi-registrazione: source file name without extension; immutable
- **eventi-elaborazione** (consumed/implemented) — owner `eventi-pubblicati`, supplier `esegui-elaborazione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `ElaborazioneAvviata`: data class(registrazioneId: RegistrazioneId, avviataAlle: Instant) : EventoPubblicato
    - `ElaborazioneCompletata`: data class(registrazioneId: RegistrazioneId) : EventoPubblicato
    - `ElaborazioneFallita`: data class(registrazioneId: RegistrazioneId, motivo: String) : EventoPubblicato — motivo in plain Italian
  - keys (minting rules):
    - `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; also names audio/<id>.<ext>, cache/audio/<id>.wav and every EstrattoRef
  - delivery: in-process, AFTER COMMIT only (never on rollback), at-least-once, on a background coroutine, coalesced per registrazioneId; subscribers must be idempotent (INV-23); single writer per key (one process, one DB) so no cross-stream reordering hazard
- **tec-decodifica-trascrizione** (consumed/implemented) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `DecodificatoreAudio`: interface { fun decodifica(id: RegistrazioneId, sorgente: RiferimentoAudio); fun tutti(id: RegistrazioneId): CampioniAudio; fun campioni(id: RegistrazioneId, intervallo: IntervalloMs): CampioniAudio } — infra faults throw (ADR 0003); campioni count = (fine-inizio)*16
- **tec-diarizzatore** (consumed/implemented) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `Diarizzatore`: interface { fun diarizza(c: CampioniAudio): List<Turno> }
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

Sources: ADRs 0002, 0003, 0004, 0005, 0006, 0007, 0011, 0012 (.mismagent/decisions/); features/trascrizione-con-parlanti/tactical-model.md § Trascrizione (INV-5, startup policy), ADR 0004/0012, architecture.md § Pipeline.
