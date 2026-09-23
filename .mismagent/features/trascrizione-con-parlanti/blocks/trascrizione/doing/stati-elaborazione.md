---
id: "stati-elaborazione"
type: "read-model"
context: "trascrizione"
side: "app"
wave: 5
module: ":trascrizione:applicazione (..letture)"
consumes:
  - "kernel-pl"
  - "agg-elaborazione"
  - "agg-trascritto"
  - "repo-trascrizione"
  - "tec-segnalatore-fase"
depends_on: []
related_adrs:
  - "0002"
  - "0003"
  - "0004"
  - "0006"
  - "0007"
  - "0012"
view_shape:
  stati: "List<{registrazioneId, stato: StatoElaborazioneVista, fase: FaseElaborazione?, avviataAlle: Instant?, motivoFallimento: String?, posizioneInCoda: Int?, numVoci: Int?}>"
view_sources:
  stati: "stato ← latest Elaborazione (mapped via named predicates to StatoElaborazioneVista); fase ← in-memory FasiInCorso implementing SegnalatoreFase; avviataAlle, motivoFallimento ← Elaborazione; posizioneInCoda ← rank among ElaborazioneRepository.inAttesa (FIFO); numVoci ← Trascritto"
---
# stati-elaborazione — StatiElaborazione — fetta Trascrizione (S2) + FasiInCorso

## What to do
Trascrizione slice of S2 (R1 split) + the in-memory FasiInCorso holder (implements SegnalatoreFase; wired by :avvio).

### view_shape (field ← source)
- `stati`: List<{registrazioneId, stato: StatoElaborazioneVista, fase: FaseElaborazione?, avviataAlle: Instant?, motivoFallimento: String?, posizioneInCoda: Int?, numVoci: Int?}> ← stato ← latest Elaborazione (mapped via named predicates to StatoElaborazioneVista); fase ← in-memory FasiInCorso implementing SegnalatoreFase; avviataAlle, motivoFallimento ← Elaborazione; posizioneInCoda ← rank among ElaborazioneRepository.inAttesa (FIFO); numVoci ← Trascritto

## Tasks
- AC-162 La vista espone stato, fase, avviataAlle, motivoFallimento, posizioneInCoda e numVoci per Registrazione
- AC-163 posizioneInCoda segue l'ordine FIFO delle in_attesa (1 = la prossima)
- AC-164 fase è presente solo per in_corso e riflette l'ultima fase segnalata; scompare quando l'Elaborazione termina
- AC-165 numVoci è presente solo per completata

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
- **tec-segnalatore-fase** (consumed/implemented) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `SegnalatoreFase`: interface { fun fase(id: RegistrazioneId, f: FaseElaborazione); fun terminata(id: RegistrazioneId) }
    - `FaseElaborazione`: enum DECODIFICA | DIARIZZAZIONE | TRASCRIZIONE | ALLINEAMENTO (trascrizione:applicazione) — progress only, not guarded state

Sources: ADRs 0002, 0003, 0004, 0006, 0007, 0012 (.mismagent/decisions/); features/trascrizione-con-parlanti/UI/ux-proposal.md S2 (+ R1), architecture.md § Pipeline.
