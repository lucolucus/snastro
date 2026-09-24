---
id: "porte-parlanti"
type: "port"
context: "parlanti"
side: "app"
wave: 3
release: "R2"
module: ":parlanti:applicazione (..porte) + testFixtures"
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
  - "0009"
  - "0012"
  - "0017"
owns_boundaries:
  repo-parlanti:
    projection: "in-process"
    contract_test: "consumer-driven"
    pinned_types:
      ParlanteRepository: "interface { trova(id: ParlanteId): Parlante?; delProgetto(id: ProgettoId): List<Parlante>; nomeAttivoInUso(progettoId, nome: Nome, escluso: ParlanteId?): Boolean; salva(p: Parlante): Esito<Unit> /* Errore(NomeGiaInUso) from the index */; rimuovi(id: ParlanteId) /* ONLY for INV-25 occasionale cessation */; impronteDiRegistrazione(id: RegistrazioneId): List<RigaImpronta>; impronteDelProgetto(id: ProgettoId): List<RigaImpronta>; aggiornaImpronta(attesa: RigaImpronta, impronta: Impronta, sorgente: String, modello: String): Boolean /* compare-and-set UPDATE of the ONE row (attesa.parlanteId, attesa.voceRef) only if it still exists with sorgente == attesa.sorgente AND modello == attesa.modello; true iff 1 row updated; NEVER inserts (ADR 0009/0012 Amendment (b): no resurrection) */ }"
      RigaImpronta: "data class(parlanteId: ParlanteId, voceRef: VoceRef, sorgente: String, modello: String) in parlanti:applicazione.porte — print row metadata, never the embedding"
      AttribuzioneRepository: "interface { trova(v: VoceRef): Attribuzione?; diRegistrazione(id: RegistrazioneId): List<Attribuzione>; diParlante(id: ParlanteId): List<Attribuzione>; salva(a: Attribuzione); rimuovi(v: VoceRef) }"
  tec-decodifica-parlanti:
    projection: "in-process"
    contract_test: "consumer-driven"
    pinned_types:
      DecodificatoreAudio: "interface { fun campioni(id: RegistrazioneId, intervalli: List<IntervalloMs>): CampioniAudio } — concatenation in the given order (Parlanti's own copy); NEVER called while a UnitaDiLavoro transaction is open (ADR 0012 Amendment (b))"
      DecodificatoreAudioFinta: "testFixtures — takes the UnitaDiLavoroFinta (optional ctor param) and throws IllegalStateException when campioni is invoked while transazioneAperta"
  tec-estrattore-impronta:
    projection: "in-process"
    contract_test: "consumer-driven"
    pinned_types:
      EstrattoreImpronta: "interface { val modello: String /* catalogue id of the embedding model (ADR 0008), stored as impronta_vocale.modello_impronta */; fun estrai(c: CampioniAudio): Impronta } — NEVER called while a UnitaDiLavoro transaction is open; the adapter serializes native use with the pipeline by taking the native Mutex INSIDE estrai (ADR 0012 Amendment (b) points 2, 5); ONE conSessione per estrai call, for ONE print, never kept after return; an interrupt (cancellation) while waiting for the Mutex or during the native extraction → InterruptedException after the session closes, and no Impronta (ADR 0017 §1.2, §1.5)"
      EstrattoreImprontaFinta: "testFixtures — takes the UnitaDiLavoroFinta (optional ctor param) and throws IllegalStateException when estrai is invoked while transazioneAperta; modello configurable (default \"finto\")"
      Impronta: "see agg-parlante (parlanti:dominio)"
  tec-confronto-impronte:
    projection: "in-process"
    contract_test: "consumer-driven"
    pinned_types:
      ConfrontoImpronte: "interface { fun fascia(voce: Impronta, impronte: List<Impronta>): Fascia } — the BEST band over the Parlante's prints; empty list → NESSUNA"
      Fascia: "enum FORTE | DEBOLE | NESSUNA (parlanti:applicazione) — never a number outside the adapter"
      SoglieFascia: "data class(forte: Double, debole: Double) — require forte > debole; values from spike impronta-vocale-affidabilita, injected as config"
---
# porte-parlanti — Porte dei Parlanti: repository e ML

## What to do
Declare ParlanteRepository (incl. rimuovi for INV-25, the compare-and-set aggiornaImpronta and the RigaImpronta reads per registrazione / per progetto), AttribuzioneRepository, DecodificatoreAudio (own copy), EstrattoreImpronta (+ modello), ConfrontoImpronte (+ Fascia, SoglieFascia) with Finta + Contratto each; ParlanteRepositoryFinta honours INV-16 like the index; EstrattoreImprontaFinta / DecodificatoreAudioFinta throw when invoked while the UnitaDiLavoroFinta has a transaction open.

Note: AMENDED 2026-09-23 (ADR 0012 Amendment (b)): EstrattoreImpronta.modello; ParlanteRepository compare-and-set aggiornaImpronta + impronteDiRegistrazione / impronteDelProgetto (RigaImpronta); ML Finte guard against an open transaction. FOLLOW-UP REQUIRED: merged before ADR 0012 Amendment (b); the merged code does not yet satisfy the amended criteria above — a rework/fix block must land them.

## Tasks
- AC-37 ParlanteRepositoryContratto: un secondo attivo con lo stesso nome normalizzato nello stesso Progetto → NomeGiaInUso; il nome di un eliminato è accettato; round-trip con impronte (passa contro la Finta)
- AC-38 AttribuzioneRepositoryContratto: chiave VoceRef, diRegistrazione, diParlante, rimuovi
- AC-39 DecodificatoreAudioContratto (parlanti): campioni di più intervalli = concatenazione nell'ordine dato
- AC-40 EstrattoreImprontaContratto: le stesse CampioniAudio producono la stessa Impronta, di dimensione costante
- AC-41 ConfrontoImpronteContratto: impronta identica → FORTE; lista vuota → NESSUNA; restituisce la fascia migliore tra le impronte; SoglieFascia con forte <= debole rifiutate
- AC-270 ParlanteRepositoryContratto: aggiornaImpronta aggiorna la riga solo se esiste ancora con sorgente e modello uguali a quelli attesi (true); riga assente o sorgente/modello cambiati → false e nulla è scritto; non inserisce mai una riga (conteggio invariato)
- AC-271 ParlanteRepositoryContratto: impronteDiRegistrazione e impronteDelProgetto restituiscono tutte e sole le RigaImpronta (parlanteId, voceRef, sorgente, modello) della Registrazione / del Progetto; il round-trip di un Parlante conserva sorgente e modello di ogni impronta
- AC-272 EstrattoreImprontaFinta e DecodificatoreAudioFinta lanciano IllegalStateException se invocate mentre la UnitaDiLavoroFinta passata ha transazioneAperta = true; fuori dalla transazione rispondono normalmente
- AC-273 EstrattoreImprontaContratto: modello è non vuoto e costante per l'istanza

## Dependencies
- **repo-parlanti** (OWNED here — built before its consumers) — owner `porte-parlanti`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `ParlanteRepository`: interface { trova(id: ParlanteId): Parlante?; delProgetto(id: ProgettoId): List<Parlante>; nomeAttivoInUso(progettoId, nome: Nome, escluso: ParlanteId?): Boolean; salva(p: Parlante): Esito<Unit> /* Errore(NomeGiaInUso) from the index */; rimuovi(id: ParlanteId) /* ONLY for INV-25 occasionale cessation */; impronteDiRegistrazione(id: RegistrazioneId): List<RigaImpronta>; impronteDelProgetto(id: ProgettoId): List<RigaImpronta>; aggiornaImpronta(attesa: RigaImpronta, impronta: Impronta, sorgente: String, modello: String): Boolean /* compare-and-set UPDATE of the ONE row (attesa.parlanteId, attesa.voceRef) only if it still exists with sorgente == attesa.sorgente AND modello == attesa.modello; true iff 1 row updated; NEVER inserts (ADR 0009/0012 Amendment (b): no resurrection) */ }
    - `RigaImpronta`: data class(parlanteId: ParlanteId, voceRef: VoceRef, sorgente: String, modello: String) in parlanti:applicazione.porte — print row metadata, never the embedding
    - `AttribuzioneRepository`: interface { trova(v: VoceRef): Attribuzione?; diRegistrazione(id: RegistrazioneId): List<Attribuzione>; diParlante(id: ParlanteId): List<Attribuzione>; salva(a: Attribuzione); rimuovi(v: VoceRef) }
- **tec-decodifica-parlanti** (OWNED here — built before its consumers) — owner `porte-parlanti`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `DecodificatoreAudio`: interface { fun campioni(id: RegistrazioneId, intervalli: List<IntervalloMs>): CampioniAudio } — concatenation in the given order (Parlanti's own copy); NEVER called while a UnitaDiLavoro transaction is open (ADR 0012 Amendment (b))
    - `DecodificatoreAudioFinta`: testFixtures — takes the UnitaDiLavoroFinta (optional ctor param) and throws IllegalStateException when campioni is invoked while transazioneAperta
- **tec-estrattore-impronta** (OWNED here — built before its consumers) — owner `porte-parlanti`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `EstrattoreImpronta`: interface { val modello: String /* catalogue id of the embedding model (ADR 0008), stored as impronta_vocale.modello_impronta */; fun estrai(c: CampioniAudio): Impronta } — NEVER called while a UnitaDiLavoro transaction is open; the adapter serializes native use with the pipeline by taking the native Mutex INSIDE estrai (ADR 0012 Amendment (b) points 2, 5); ONE conSessione per estrai call, for ONE print, never kept after return; an interrupt (cancellation) while waiting for the Mutex or during the native extraction → InterruptedException after the session closes, and no Impronta (ADR 0017 §1.2, §1.5)
    - `EstrattoreImprontaFinta`: testFixtures — takes the UnitaDiLavoroFinta (optional ctor param) and throws IllegalStateException when estrai is invoked while transazioneAperta; modello configurable (default "finto")
    - `Impronta`: see agg-parlante (parlanti:dominio)
- **tec-confronto-impronte** (OWNED here — built before its consumers) — owner `porte-parlanti`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `ConfrontoImpronte`: interface { fun fascia(voce: Impronta, impronte: List<Impronta>): Fascia } — the BEST band over the Parlante's prints; empty list → NESSUNA
    - `Fascia`: enum FORTE | DEBOLE | NESSUNA (parlanti:applicazione) — never a number outside the adapter
    - `SoglieFascia`: data class(forte: Double, debole: Double) — require forte > debole; values from spike impronta-vocale-affidabilita, injected as config
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

Sources: ADRs 0002, 0003, 0004, 0005, 0006, 0007, 0009, 0012, 0017 (.mismagent/decisions/); features/trascrizione-con-parlanti/architetture/architecture-overview.md (Technical ports), ADR 0004/0009.
