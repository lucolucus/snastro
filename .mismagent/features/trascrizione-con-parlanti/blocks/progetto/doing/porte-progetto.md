---
id: "porte-progetto"
type: "port"
context: "progetto"
side: "app"
wave: 3
release: "R0"
module: ":progetto:applicazione (..porte) + testFixtures"
consumes:
  - "kernel-pl"
depends_on: []
related_adrs:
  - "0002"
  - "0003"
  - "0005"
  - "0006"
  - "0010"
  - "0012"
owns_boundaries:
  repo-progetto:
    projection: "in-process"
    contract_test: "consumer-driven"
    pinned_types:
      ProgettoRepository: "interface { trova(): Progetto?; salva(p: Progetto) } — one Progetto per project DB"
      RegistrazioneRepository: "interface { trova(id: RegistrazioneId): Registrazione?; delProgetto(id: ProgettoId): List<Registrazione>; titoliDelProgetto(id: ProgettoId): List<String> /* titles only, no order, for the titolo uniqueness of AggiungiRegistrazione (AC-322) */; salva(r: Registrazione) }"
  tec-registro-progetti:
    projection: "in-process"
    contract_test: "consumer-driven"
    pinned_types:
      RegistroProgetti: "interface { elenco(): List<VoceRegistro> /* by ultimaAttivita desc */; registra(v: VoceRegistro); aggiorna(percorso: String, numRegistrazioni: Int, ultimaAttivita: Instant) /* keyed by percorso like registra/rimuovi; unknown percorso → no-op */; rimuovi(percorso: String) }"
      VoceRegistro: "data class(progettoId: ProgettoId, nome: String, percorso: String, numRegistrazioni: Int, ultimaAttivita: Instant)"
  tec-sonda-archivio:
    projection: "in-process"
    contract_test: "consumer-driven"
    pinned_types:
      SondaAudio: "interface { fun sonda(percorsoSorgente: String): Esito<InfoAudio> } — Errore(AudioNonLeggibile | FormatoNonSupportato)"
      InfoAudio: "data class(durataMs: Long, dataFile: LocalDate)"
      ArchivioAudio: "interface { fun copia(percorsoSorgente: String, id: RegistrazioneId): Esito<RiferimentoAudio>; fun scarta(r: RiferimentoAudio) } — Errore(CopiaFallita) leaves no partial file"
---
# porte-progetto — Porte del Progetto: repository, sonda/archivio audio, registro progetti

## What to do
Declare ProgettoRepository, RegistrazioneRepository, SondaAudio, ArchivioAudio, RegistroProgetti (R3) with a Finta and an abstract Contratto each in testFixtures.

Note: AMENDED 2026-09-23: (i) tec-registro-progetti re-pinned to the MERGED port aggiorna(percorso: String, numRegistrazioni: Int, ultimaAttivita: Instant) — manifest drift fixed, no code change; (ii) new RegistrazioneRepository.titoliDelProgetto(id: ProgettoId): List<String> (repo-progetto) for the titolo uniqueness of AggiungiRegistrazione. FOLLOW-UP REQUIRED for (ii): merged before this amendment — the port, its Finta and RegistrazioneRepositoryContratto must gain titoliDelProgetto (AC-325).

## Tasks
- AC-25 ProgettoRepositoryContratto e RegistrazioneRepositoryContratto passano contro le Finte (round-trip, delProgetto)
- AC-26 SondaAudioContratto: file leggibile → durata > 0 e data del file; illeggibile → AudioNonLeggibile; formato non supportato → FormatoNonSupportato (passa contro SondaAudioFinta)
- AC-27 ArchivioAudioContratto: copia → RiferimentoAudio 'audio/<registrazioneId>.<ext minuscola>'; copia fallita → CopiaFallita e nessun file residuo
- AC-28 RegistroProgettiContratto: registra poi elenco lo contiene; registrare due volte lo stesso percorso non duplica; aggiorna(percorso, …) cambia numRegistrazioni e ultimaAttivita; elenco ordinato per ultimaAttivita decrescente
- AC-325 RegistrazioneRepositoryContratto: titoliDelProgetto(id) restituisce i titoli di tutte e sole le Registrazioni di quel Progetto (lista vuota se nessuna, nessun ordine garantito) — passa contro RegistrazioneRepositoryFinta

## Dependencies
- **repo-progetto** (OWNED here — built before its consumers) — owner `porte-progetto`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `ProgettoRepository`: interface { trova(): Progetto?; salva(p: Progetto) } — one Progetto per project DB
    - `RegistrazioneRepository`: interface { trova(id: RegistrazioneId): Registrazione?; delProgetto(id: ProgettoId): List<Registrazione>; titoliDelProgetto(id: ProgettoId): List<String> /* titles only, no order, for the titolo uniqueness of AggiungiRegistrazione (AC-322) */; salva(r: Registrazione) }
- **tec-registro-progetti** (OWNED here — built before its consumers) — owner `porte-progetto`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `RegistroProgetti`: interface { elenco(): List<VoceRegistro> /* by ultimaAttivita desc */; registra(v: VoceRegistro); aggiorna(percorso: String, numRegistrazioni: Int, ultimaAttivita: Instant) /* keyed by percorso like registra/rimuovi; unknown percorso → no-op */; rimuovi(percorso: String) }
    - `VoceRegistro`: data class(progettoId: ProgettoId, nome: String, percorso: String, numRegistrazioni: Int, ultimaAttivita: Instant)
  - keys (minting rules):
    - `percorso`: minted by avvio-r0 (SessioneProgetto crea/apri): absolute path of the <nome>.snastro folder as an opaque string; the registry is keyed by it — a moved folder re-registers on open
    - `ProgettoId`: minted by crea-progetto via kernel GeneratoreId (UUID v4 string) — immutable; stored in progetto.db so it survives moving/copying the project folder
- **tec-sonda-archivio** (OWNED here — built before its consumers) — owner `porte-progetto`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `SondaAudio`: interface { fun sonda(percorsoSorgente: String): Esito<InfoAudio> } — Errore(AudioNonLeggibile | FormatoNonSupportato)
    - `InfoAudio`: data class(durataMs: Long, dataFile: LocalDate)
    - `ArchivioAudio`: interface { fun copia(percorsoSorgente: String, id: RegistrazioneId): Esito<RiferimentoAudio>; fun scarta(r: RiferimentoAudio) } — Errore(CopiaFallita) leaves no partial file
  - keys (minting rules):
    - `RiferimentoAudio`: minted by audio-progetto (ArchivioAudio.copia): 'audio/<registrazioneId>.<source extension lowercased>', relative to the project folder — immutable
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

Sources: ADRs 0002, 0003, 0005, 0006, 0010, 0012 (.mismagent/decisions/); features/trascrizione-con-parlanti/architetture/architecture-overview.md (Technical ports), dev-architecture-app.md #porta-contratto.
