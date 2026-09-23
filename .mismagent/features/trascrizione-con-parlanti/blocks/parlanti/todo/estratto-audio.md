---
id: "estratto-audio"
type: "read-model"
context: "parlanti"
side: "app"
wave: 4
release: "R2"
module: ":parlanti:applicazione (..letture)"
consumes:
  - "kernel-pl"
  - "voci-per-parlanti"
  - "sorgente-impronta-pl"
depends_on: []
related_adrs:
  - "0002"
  - "0003"
  - "0005"
  - "0009"
  - "0012"
view_shape:
  EstrattoRef: "registrazioneId, intervalli: List<IntervalloMs>"
view_sources:
  EstrattoRef: "← voci-per-parlanti (intervalli of the Voce) through sorgente-impronta-pl selezionaIntervalli(intervalli, BUDGET_ESTRATTO_MS, MAX_INTERVALLI_ESTRATTO)"
---
# estratto-audio — EstrattoAudio di una Voce

## What to do
estratto(voceRef): EstrattoRef? — the Voce's intervals through the shared selezionaIntervalli(intervalli, BUDGET_ESTRATTO_MS = 10 000, MAX_INTERVALLI_ESTRATTO = 3) of sorgente-impronta (overlaps merged, >= 1 000 ms filter with single-longest fallback, longest first, last clipped from its start, time order). Shared by proposta, parlanti-del-progetto and S3 (rule 11 → built before them).

Note: AMENDED 2026-09-23 (ADR 0012 Amendment (b) point 1): the selection is NOT re-implemented here — it calls the shared pure function of sorgente-impronta (supersedes the looser '2–3 longest, about 10 s' reading).

### view_shape (field ← source)
- `EstrattoRef`: registrazioneId, intervalli: List<IntervalloMs> ← ← voci-per-parlanti (intervalli of the Voce) through sorgente-impronta-pl selezionaIntervalli(intervalli, BUDGET_ESTRATTO_MS, MAX_INTERVALLI_ESTRATTO)

## Tasks
- AC-105 L'estratto applica la regola condivisa selezionaIntervalli(intervalli della Voce, 10 000 ms, 3): sovrapposizioni fuse, Segmenti >= 1 000 ms (altrimenti il solo più lungo), i più lunghi per primi (parità: inizio minore), mai più di 3 intervalli disgiunti, totale <= 10 000 ms, ordinati per inizio
- AC-106 Una Voce con un solo Segmento di 30 s → un intervallo tagliato a 10 s dal suo inizio
- AC-107 Voce inesistente o Registrazione senza Trascritto → nessun estratto (null)

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
- **voci-per-parlanti** (consumed/implemented) — owner `porta-lettore-voci`, supplier `api-trascritto`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `LettoreVoci`: interface { fun voci(id: RegistrazioneId): List<VoceVista>? } — null iff no Trascritto (INV-5); Voci ordered by voceId
    - `VoceVista`: data class(voceRef: VoceRef, intervalli: List<IntervalloMs>) — intervals of the Voce's current Segmenti, ordered by inizioMs (tie: segmentoId); NEVER text
  - keys (minting rules):
    - `VoceRef`: composite (registrazioneId, voceId), typed kernel VO because >=2 contexts use it — correlation key of Attribuzione, ImprontaVocale and the Documento name map; stable as its parts
    - `VoceId`: minted by the trascritto aggregate from its persisted counter prossimaVoce — at creation 1..n in order of FIRST APPEARANCE (smallest turn inizioMs, tie: diarizer voceIndice); DividiVoce / riassegna-to-new take prossimaVoce++; never reused, never renumbered, == the n of the label 'Voce n'; stable for the Trascritto's life (= forever: no re-run after completata)
- **sorgente-impronta-pl** (consumed/implemented) — owner `sorgente-impronta`, projection in-process, contract_test **invariant-test**
  - pinned types:
    - `SorgenteImpronta`: data class(intervalli: List<IntervalloMs>) in snastro.parlanti.dominio — non-empty, pairwise disjoint, ordered by inizioMs; val chiave: String = intervalli joined as "<inizioMs>-<fineMs>" with "," (e.g. "1200-5400,8000-15000")
    - `SorgenteImpronta.di`: (intervalliVoce: List<IntervalloMs>): SorgenteImpronta = SorgenteImpronta(selezionaIntervalli(intervalliVoce, BUDGET_IMPRONTA_MS, maxIntervalli = null)) — require intervalliVoce non-empty; the ONLY way any print (ConfermaAttribuzione, SaltaVoce, RiallineaImpronte, transient Proposta print) chooses the audio to decode
    - `selezionaIntervalli`: (intervalli: List<IntervalloMs>, budgetMs: Long, maxIntervalli: Int?): List<IntervalloMs> — the ONE shared pure selection function (parlanti:dominio): (1) merge overlapping intervals into their union; (2) keep those >= DURATA_MINIMA_SEGMENTO_MS, else the single longest; (3) longest first, tie earlier inizioMs; (4) accumulate up to budgetMs and at most maxIntervalli, the crossing interval trimmed from its start to [inizio, inizio + resto]; (5) return disjoint, in time order
    - `BUDGET_IMPRONTA_MS`: const val Long = 30_000L — PROVISIONAL (spike impronta-vocale-affidabilita calibrates it; final value in its closing ADR)
    - `DURATA_MINIMA_SEGMENTO_MS`: const val Long = 1_000L
    - `BUDGET_ESTRATTO_MS`: const val Long = 10_000L — EstrattoAudio budget
    - `MAX_INTERVALLI_ESTRATTO`: const val Int = 3 — EstrattoAudio interval cap
    - `ErroreParlanti.VoceCambiata`: data class(voceRef: VoceRef) : ErroreParlanti (file ErroriParlanti.kt, parlanti:dominio) — the Voce's SorgenteImpronta changed between the extraction and the command's transaction; nothing written, the user retries
  - keys (minting rules):
    - `chiave`: minted by SorgenteImpronta (sorgente-impronta) from its final disjoint intervals in time order, "<inizioMs>-<fineMs>" joined by ","; deterministic for equal intervals; changes whenever the Voce's Segmenti or BUDGET_IMPRONTA_MS change (that IS the staleness signal); stored as impronta_vocale.sorgente_impronta

Sources: ADRs 0002, 0003, 0005, 0009, 0012 (.mismagent/decisions/); features/trascrizione-con-parlanti/tactical-model.md § Parlanti (EstrattoAudio) + R24.
