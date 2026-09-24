---
id: "schermata-registrazione"
type: "ui"
context: "ui"
side: "app"
wave: 8
release: "R1"
module: ":ui (snastro.ui.registrazione)"
consumes:
  - "kernel-pl"
  - "tec-lettore-audio"
  - "tec-shell-ui"
depends_on:
  - "trascritto-view"
  - "documento"
  - "ui-fondamenta"
  - "stati-elaborazione"
related_adrs:
  - "0002"
  - "0003"
  - "0005"
  - "0010"
  - "0012"
  - "0018"
consumes_rm:
  - "trascritto-view"
  - "documento"
  - "stati-elaborazione"
triggers: []
---
# schermata-registrazione — S3 · Registrazione (trascritto per Voce, sola lettura in R1)

## What to do
S3 in R1, READ-ONLY (concept B without the Voci panel): header with title, date, audio bar and 'Apri documento' / 'Mostra nella cartella' (ApriEsterno + documento.nomeFile); centre transcript of trascritto-view Segmenti in time order across Voci, each with its colour dot and 'Voce n' label; click/'▶' on a Segmento plays from its inizio via LettoreAudio and highlights it. The presenter takes the Parlanti sources and the Revisione/identification commands as OPTIONAL inputs, absent in R1 (AC-402); no Revisione UI in R1 (explicit cut — it arrives with schermata-registrazione-identificazione, R2).

REWORK 2026-09-24 (ADR 0018): + optional stati-elaborazione source: read-only flag + banner 'Ritrascrizione in corso: modifiche disabilitate fino al termine' while a re-run is queued or running (AC-452); reload on the replacement (AC-453).

Note: Declared: Documento content is never shown in-app (ux decision). AMENDED 2026-09-24 (manifest delta 2026-09-24-packaging, user decisions 2026-09-24 — variant A, READ-ONLY): R1 S3 only reads — text by Voce with 'Voce n' labels, play from each Segmento, open/show the Documento. NO gated_by: the attesa-mutex-estrazione gate moved to R2 with the Parlanti features. EXPLICIT CUT (rule 9, ux-proposal amendment 2026-09-24 S3): the Voci panel (AC-212/213/214/216/219/318/319, AC-215, excerpts half of AC-217 = AC-403) AND the Revisione UI (selection, 'Dividi voce', 'Riassegna a', 'Unisci con' — AC-209/210/211, Revisione error = AC-404) moved to the R2 block schermata-registrazione-identificazione (S3 panel; NOT schermata-registrazioni-identificazione, which is the S2 badge). The already-built revisione domain block stays in R1 with no UI. 'Voce n' labels only (no Nome) — the Nome/colour join from identificazione-voci is R2. AMENDED 2026-09-24 (ADR 0018 + Amendment 2026-09-24 (b), manifest delta 2026-09-24-ritrascrivi): + the OPTIONAL stati-elaborazione source (state of the shown Registrazione) → the read-only flag + banner (AC-452) and the reload on replacement (AC-453). It adds no command: in R1 S3 has none, and in R2 the panel block disables its own (AC-454).

### Consumes read-models: trascritto-view, documento, stati-elaborazione
### Triggers: —

## Tasks
- AC-207 Caricamento: scheletro del trascritto
- AC-208 Click su un Segmento → riproduzione dal suo inizio e Segmento evidenziato
- AC-217 (REWRITTEN 2026-09-24, metà R1; la metà estratti delle Voci è AC-403) Audio sorgente mancante (LettoreAudio.disponibile = false) → barra audio disabilitata con messaggio, il click su un Segmento non riproduce, il trascritto resta leggibile
- AC-218 'Apri documento' e 'Mostra nella cartella' usano il percorso del Documento
- AC-402 (R1, specchio di AC-342/AC-345) Le sorgenti Parlanti del presenter di S3 (identificazione-voci, proposta, proposta-unione, parlanti-attivi, estratto-audio) e i comandi ConfermaAttribuzione, SaltaVoce, UnisciVoci, DividiVoce e RiassegnaSegmento sono OPZIONALI: il presenter costruito senza di essi (R1, avvio-composizione) mostra i Segmenti in ordine di tempo attraverso le Voci, ciascuno con il pallino colorato e l'etichetta 'Voce n' di trascritto-view, e '▶'/click da ogni Segmento; nessun pannello Voci, card, banner di unione, 'conferma'/'salta'/'cambia'/'Unisci con', selezione multipla né barra di selezione, nessun '▶ estratto'; mai un conteggio provvisorio né una galleria vuota; nessuna azione della schermata raggiunge il Mutex nativo — test del presenter con le sole finte di TrascrittoView, Documento, LettoreAudio e ApriEsterno
- AC-452 (Amendment 2026-09-24 (b): S3 read-only) While the latest Elaborazione of the shown Registrazione is in_attesa / in_corso and a Trascritto exists, S3 is READ-ONLY and shows the banner 'Ritrascrizione in corso: modifiche disabilitate fino al termine' with the second line 'Questa trascrizione sarà sostituita quando la nuova sarà pronta.'; the presenter exposes this read-only flag for the R2 panel; the transcript stays readable and playable ('▶'/click on a Segmento, 'Apri documento', 'Mostra nella cartella'); banner and flag go away on the next Cambiamento that shows another state (completata, fallita, or the run cancelled). Without the optional stati source there is no banner (R1 test: never read-only)
- AC-453 On the Cambiamento after a replacement S3 reloads: the new Segmenti and 'Voce n' labels are shown, any selection is cleared, playback of an old Segmento stops (no stale Segmento id is kept) — presenter test with a fake view source swapped between two generations
- (rendering — sizing/overflow/contrast/state rendering at 1280x800 and 1024x640 — is owned by realize-ui + `./gradlew :ui:renderCheck`, not a tests_nl item)

## Dependencies
- Blocks built first: `trascritto-view` (wave 5), `documento` (wave 4), `ui-fondamenta` (wave 6), `stati-elaborazione` (wave 5)
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
- **tec-lettore-audio** (consumed/implemented) — owner `lettore-audio`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `LettoreAudio`: interface { fun disponibile(id: RegistrazioneId): Boolean; fun riproduciDa(id: RegistrazioneId, daMs: Long); fun riproduciEstratto(e: EstrattoRef); fun pausa(); val stato: StateFlow<StatoLettore> }
    - `StatoLettore`: data class(registrazioneId: RegistrazioneId?, posizioneMs: Long, inRiproduzione: Boolean)
- **tec-shell-ui** (consumed/implemented) — owner `ui-fondamenta`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `SessioneProgetto`: interface { val corrente: StateFlow<ProgettoAperto?>; fun crea(cartellaGenitore: String, nome: String): Esito<ProgettoAperto>; fun apri(percorso: String): Esito<ProgettoAperto>; fun chiudi() }
    - `ProgettoAperto`: data class(progettoId: ProgettoId, nome: String, percorso: String)
    - `ErroreSessione`: sealed interface : ErroreDominio (file ErroriSessione.kt) { NomeProgettoVuoto; CartellaNonValida; ProgettoGiaAperto; DatabasePiuRecente } — no CartellaGiaEsistente: crea derives a free folder name (AC-264), re-pinned 2026-09-23 (user decision)
    - `ApriEsterno`: interface { fun apriFile(percorso: String); fun mostraNellaCartella(percorso: String) }
    - `AggiornamentiVista`: interface { val cambiamenti: Flow<Cambiamento> }
    - `Cambiamento`: data class(registrazioneId: RegistrazioneId?) — null = everything may have changed
  - keys (minting rules):
    - `percorso`: see tec-registro-progetti

Sources: ADRs 0002, 0003, 0005, 0010, 0012, 0018 (.mismagent/decisions/); features/trascrizione-con-parlanti/UI/ux-proposal.md S3 (+ R1, R8, amendment 2026-09-24 S3 read-only in R1), manifest delta 2026-09-24-packaging (User decisions 2026-09-24).
