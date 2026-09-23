---
id: "sorgente-impronta"
type: "aggregate"
context: "parlanti"
side: "app"
wave: 2
module: ":parlanti:dominio"
consumes:
  - "kernel-pl"
depends_on: []
related_adrs:
  - "0002"
  - "0003"
  - "0009"
  - "0012"
invariants:
  - "Selezione — selection rule (ADR 0012 Amendment (b) point 1): overlapping input intervals are first merged into their union; keep intervals >= DURATA_MINIMA_SEGMENTO_MS (1 000), or the single longest if none reaches it; take longest first (tie: earlier inizioMs) up to the budget (and maxIntervalli if given), trimming the crossing interval from its start to [inizio, inizio + resto]; return disjoint intervals in time order"
  - "Chiave — chiave = the final intervals in time order encoded as \"<inizioMs>-<fineMs>\" joined by \",\" — exact, collision-free, no hashing; equal sources ⇔ equal chiavi"
invariant_fields: []
identity: "none — value object (no identity, no table; its chiave is stored by the Parlante's ImprontaVocale)"
tables: []
owns_boundaries:
  sorgente-impronta-pl:
    projection: "in-process"
    contract_test: "invariant-test"
    pinned_types:
      SorgenteImpronta: "data class(intervalli: List<IntervalloMs>) in snastro.parlanti.dominio — non-empty, pairwise disjoint, ordered by inizioMs; val chiave: String = intervalli joined as \"<inizioMs>-<fineMs>\" with \",\" (e.g. \"1200-5400,8000-15000\")"
      "SorgenteImpronta.di": "(intervalliVoce: List<IntervalloMs>): SorgenteImpronta = SorgenteImpronta(selezionaIntervalli(intervalliVoce, BUDGET_IMPRONTA_MS, maxIntervalli = null)) — require intervalliVoce non-empty; the ONLY way any print (ConfermaAttribuzione, SaltaVoce, RiallineaImpronte, transient Proposta print) chooses the audio to decode"
      selezionaIntervalli: "(intervalli: List<IntervalloMs>, budgetMs: Long, maxIntervalli: Int?): List<IntervalloMs> — the ONE shared pure selection function (parlanti:dominio): (1) merge overlapping intervals into their union; (2) keep those >= DURATA_MINIMA_SEGMENTO_MS, else the single longest; (3) longest first, tie earlier inizioMs; (4) accumulate up to budgetMs and at most maxIntervalli, the crossing interval trimmed from its start to [inizio, inizio + resto]; (5) return disjoint, in time order"
      BUDGET_IMPRONTA_MS: "const val Long = 30_000L — PROVISIONAL (spike impronta-vocale-affidabilita calibrates it; final value in its closing ADR)"
      DURATA_MINIMA_SEGMENTO_MS: "const val Long = 1_000L"
      BUDGET_ESTRATTO_MS: "const val Long = 10_000L — EstrattoAudio budget"
      MAX_INTERVALLI_ESTRATTO: "const val Int = 3 — EstrattoAudio interval cap"
      "ErroreParlanti.VoceCambiata": "data class(voceRef: VoceRef) : ErroreParlanti (file ErroriParlanti.kt, parlanti:dominio) — the Voce's SorgenteImpronta changed between the extraction and the command's transaction; nothing written, the user retries"
---
# sorgente-impronta — SorgenteImpronta — selezione limitata dell'audio di una Voce (+ regola condivisa con EstrattoAudio)

## What to do
Pure value object SorgenteImpronta in :parlanti:dominio + the ONE shared selection function selezionaIntervalli (union of overlapping intervals → >= 1 000 ms filter with single-longest fallback → longest first, tie earlier inizioMs → budget with the crossing interval trimmed from its start → time order), the constants BUDGET_IMPRONTA_MS (30 000, provisional), DURATA_MINIMA_SEGMENTO_MS, BUDGET_ESTRATTO_MS, MAX_INTERVALLI_ESTRATTO, the chiave encoding, and ErroreParlanti.VoceCambiata. No I/O, no ports.

Note: Derived owner (rule 11) of ErroreParlanti.VoceCambiata (in ErroriParlanti.kt) too (shared by conferma-attribuzione and salta-voce, same wave). BUDGET_IMPRONTA_MS is PROVISIONAL: spike impronta-vocale-affidabilita calibrates it and its closing ADR records the final value (changing it needs no migration: every print goes stale and RiallineaTutteLeImpronte re-derives it). Supersedes the looser '2–3 longest, about 10 s' reading of EstrattoAudio (R24). Overlap merge: LettoreVoci deliberately does not merge overlapping Segmenti of a Voce (api-trascritto code-review), so the union is taken HERE.

### Invariants owned here (one test each, name starts with the tag)
- Selezione — selection rule (ADR 0012 Amendment (b) point 1): overlapping input intervals are first merged into their union; keep intervals >= DURATA_MINIMA_SEGMENTO_MS (1 000), or the single longest if none reaches it; take longest first (tie: earlier inizioMs) up to the budget (and maxIntervalli if given), trimming the crossing interval from its start to [inizio, inizio + resto]; return disjoint intervals in time order
- Chiave — chiave = the final intervals in time order encoded as "<inizioMs>-<fineMs>" joined by "," — exact, collision-free, no hashing; equal sources ⇔ equal chiavi

## Tasks
- AC-274 Selezione — filtro 1 000 ms: i Segmenti < 1 000 ms sono esclusi quando almeno uno raggiunge 1 000 ms; se nessuno li raggiunge si usa il solo Segmento più lungo (a parità, quello con inizio minore)
- AC-275 Selezione — scelta: il più lungo per primo, a parità di durata quello con inizioMs minore (test: due Segmenti di pari durata e un budget che ne ammette uno solo intero)
- AC-276 Selezione — budget: totale <= BUDGET_IMPRONTA_MS (30 000); l'intervallo che supera il budget è tagliato dal suo inizio a [inizio, inizio + resto]; una Voce di oltre 30 s dà un totale di esattamente 30 000 ms
- AC-277 Selezione — il risultato è in ordine temporale (per inizioMs) indipendentemente dall'ordine di scelta
- AC-278 Chiave — chiave = "<inizioMs>-<fineMs>" uniti da "," in ordine temporale (es. [1200-5400, 8000-15000] → "1200-5400,8000-15000"); due SorgenteImpronta hanno la stessa chiave se e solo se hanno gli stessi intervalli
- AC-279 Selezione — intervalli sovrapposti in ingresso (due Segmenti di una Voce unita che si sovrappongono nel tempo, es. 0-4000 e 3000-6000) sono prima fusi nella loro unione (0-6000) e solo dopo filtrati, scelti e troncati: il risultato è disgiunto, nessun millisecondo è contato o decodificato due volte, e la chiave è calcolata sugli intervalli finali disgiunti
- AC-280 Selezione — parametri EstrattoAudio: selezionaIntervalli(intervalli, BUDGET_ESTRATTO_MS = 10 000, MAX_INTERVALLI_ESTRATTO = 3) → al più 3 intervalli disgiunti, totale <= 10 000 ms, stessa regola (fusione delle sovrapposizioni, filtro 1 000 ms con ripiego sul più lungo, taglio dall'inizio, ordine temporale)
- AC-281 SorgenteImpronta.di su una lista vuota → IllegalArgumentException (require: una Voce ha sempre >= 1 intervallo, LettoreVoci)

## Dependencies
- **sorgente-impronta-pl** (OWNED here — built before its consumers) — owner `sorgente-impronta`, projection in-process, contract_test **invariant-test**
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

Sources: ADRs 0002, 0003, 0009, 0012 (.mismagent/decisions/); ADR 0012 Amendment (b) point 1, ADR 0009 Amendment (b), features/trascrizione-con-parlanti/tactical-model.md § Parlanti (INV-15, R24 EstrattoAudio).
