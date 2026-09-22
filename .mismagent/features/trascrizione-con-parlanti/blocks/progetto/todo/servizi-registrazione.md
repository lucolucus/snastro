---
id: "servizi-registrazione"
type: "application-service"
context: "progetto"
side: "app"
wave: 4
module: ":progetto:applicazione (..comandi)"
consumes:
  - "kernel-pl"
  - "agg-registrazione"
  - "repo-progetto"
  - "eventi-progetto"
  - "tec-sonda-archivio"
depends_on: []
related_adrs:
  - "0002"
  - "0003"
  - "0005"
  - "0006"
  - "0010"
  - "0012"
commands:
  - "AggiungiRegistrazione"
  - "ModificaDataRegistrazione"
---
# servizi-registrazione — AggiungiRegistrazione, ModificaDataRegistrazione

## What to do
AggiungiRegistrazione: sonda → copia → Registrazione.aggiungi (titolo = file name without extension, durata from the probe, date = file date) → save → publish RegistrazioneAggiunta (whose SYNC subscriber queues the Elaborazione, R2). ModificaDataRegistrazione publishes DataRegistrazioneModificata.

## Tasks
- AC-56 AggiungiRegistrazione con un file leggibile crea la Registrazione con titolo = nome del file senza estensione, durata dalla sonda e DataRegistrazione = data del file, e pubblica RegistrazioneAggiunta
- AC-57 Un file illeggibile o in formato non supportato → errore, nessuna Registrazione creata e nessun file lasciato in audio/
- AC-58 Una copia fallita a metà → nulla creato (nessuna riga, nessun file parziale)
- AC-59 L'audio è copiato in audio/<registrazioneId>.<ext> e il riferimento salvato è relativo alla cartella del progetto
- AC-60 Se l'abbonato sincrono che accoda l'Elaborazione fallisce, la Registrazione non esiste (rollback dell'intero comando)
- AC-61 Aggiungere due volte lo stesso file crea due Registrazioni distinte, senza blocchi
- AC-62 ModificaDataRegistrazione sostituisce la data e pubblica DataRegistrazioneModificata(precedente, nuova)
- AC-63 ModificaDataRegistrazione su una Registrazione inesistente → RegistrazioneNonTrovata

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
    - `ErroreDominio`: interface (NOT sealed — Kotlin forbids cross-module sealed subtypes; NOT Throwable); each context declares its own sealed hierarchy Errori<Contesto> : ErroreDominio
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
- **agg-registrazione** (consumed/implemented) — owner `registrazione`, projection in-process, contract_test **invariant-test**
  - pinned types:
    - `Registrazione.aggiungi`: (id, progettoId, titolo: String, riferimentoAudio, durataMs: Long, dataRegistrazione: LocalDate, aggiuntaAlle: Instant): Creato<Registrazione, RegistrazioneAggiunta>
    - `Registrazione.modificaData`: (nuova: LocalDate): Esito<DataRegistrazioneModificata>
    - `invariant_fields exposure`: progettoId (val, immutable), dataRegistrazione (private set)
  - keys (minting rules):
    - `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; also names audio/<id>.<ext>, cache/audio/<id>.wav and every EstrattoRef
    - `RiferimentoAudio`: minted by audio-progetto (ArchivioAudio.copia): 'audio/<registrazioneId>.<source extension lowercased>', relative to the project folder — immutable
  - §14 gates (must stay green):
    - `! grep -rnE --include='*.kt' --exclude-dir=build '\b(registrazioneQueries)\b' . | grep -vE '^\./(persistenza/|progetto/adattatori/src/[A-Za-z]+/kotlin/snastro/progetto/adattatori/persistenza/)' | grep -q .`
- **repo-progetto** (consumed/implemented) — owner `porte-progetto`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `ProgettoRepository`: interface { trova(): Progetto?; salva(p: Progetto) } — one Progetto per project DB
    - `RegistrazioneRepository`: interface { trova(id: RegistrazioneId): Registrazione?; delProgetto(id: ProgettoId): List<Registrazione>; salva(r: Registrazione) }
- **eventi-progetto** (consumed/implemented) — owner `eventi-pubblicati`, supplier `crea-progetto, servizi-registrazione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `ProgettoCreato`: data class(progettoId: ProgettoId, nome: String) : EventoPubblicato
    - `RegistrazioneAggiunta`: data class(registrazioneId: RegistrazioneId, progettoId: ProgettoId) : EventoPubblicato — SYNC consumer: abbonato-registrazione-aggiunta
    - `DataRegistrazioneModificata`: data class(registrazioneId: RegistrazioneId, precedente: LocalDate, nuova: LocalDate) : EventoPubblicato — AFTER-COMMIT consumer: abbonato-documento
  - keys (minting rules):
    - `ProgettoId`: minted by crea-progetto via kernel GeneratoreId (UUID v4 string) — immutable; stored in progetto.db so it survives moving/copying the project folder
    - `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; also names audio/<id>.<ext>, cache/audio/<id>.wav and every EstrattoRef
  - delivery: RegistrazioneAggiunta → in-process, SYNCHRONOUS inside the publishing command's UnitaDiLavoro transaction, in emission order, exactly once per commit attempt; an Esito.Errore or exception from a sync subscriber rolls the whole command back (ADR 0012). Others → in-process, AFTER COMMIT only (never on rollback), at-least-once, on a background coroutine, coalesced per registrazioneId; subscribers must be idempotent (INV-23); single writer per key (one process, one DB) so no cross-stream reordering hazard
- **tec-sonda-archivio** (consumed/implemented) — owner `porte-progetto`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `SondaAudio`: interface { fun sonda(percorsoSorgente: String): Esito<InfoAudio> } — Errore(AudioNonLeggibile | FormatoNonSupportato)
    - `InfoAudio`: data class(durataMs: Long, dataFile: LocalDate)
    - `ArchivioAudio`: interface { fun copia(percorsoSorgente: String, id: RegistrazioneId): Esito<RiferimentoAudio>; fun scarta(r: RiferimentoAudio) } — Errore(CopiaFallita) leaves no partial file
  - keys (minting rules):
    - `RiferimentoAudio`: minted by audio-progetto (ArchivioAudio.copia): 'audio/<registrazioneId>.<source extension lowercased>', relative to the project folder — immutable

Sources: ADRs 0002, 0003, 0005, 0006, 0010, 0012 (.mismagent/decisions/); features/trascrizione-con-parlanti/tactical-model.md § Progetto (+ R2, R6, R24), ADR 0010.
