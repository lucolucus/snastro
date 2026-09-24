---
id: "classificatore-somiglianza"
type: "adapter"
context: "parlanti"
side: "app"
wave: 4
release: "R2"
module: ":parlanti:adattatori (..ml)"
consumes:
  - "kernel-pl"
  - "tec-classificatore-somiglianza"
depends_on: []
related_adrs:
  - "0002"
  - "0003"
  - "0004"
  - "0012"
  - "0019"
---
# classificatore-somiglianza — ClassificatoreSomiglianzaCoseno (frase → Sicura(P) | Incerta, Kotlin puro)

## What to do
ClassificatoreSomiglianzaCoseno, the pure-Kotlin implementation of ClassificatoreSomiglianza: for each frase, cosine to each reference Parlante's centroid (normalized mean of its normalized reference embeddings), then Sicura(best) iff best >= SIMILARITA_MINIMA and best − second >= MARGINE_MINIMO, else Incerta. Non-comparable prints → Incerta, never an exception. No number leaves the adapter.

Note: NEW 2026-09-24 (ADR 0019 §4.3, manifest delta 2026-09-24-semi-automatica): pure Kotlin, no natives, runs in the gate. Score = cosine to each reference Parlante's centroid (whatever its reference mode, ADR 0019 Amendment (b).1); Sicura(best) iff best >= SIMILARITA_MINIMA and best − second >= MARGINE_MINIMO, else Incerta. Thresholds PROVISIONAL [hypothesis], injected as SoglieSomiglianza (calibration: Via Roquel with the user's references, recorded by amendment); no number leaves the adapter (code review). Leave-one-out centroids are the first calibration alternative.

## Tasks
- AC-496 ClassificatoreSomiglianzaContratto passes against the real implementation, in the gate, with no models
- AC-497 With SoglieSomiglianza(0.30, 0.05) and orthogonal unit references A and B (table test): frase = A → Sicura(A); frase at 45° (cos 0.707 / 0.707) → Incerta (margin 0); cos(A) = 0.25, cos(B) = 0 → Incerta (below minima); cos 0.80 / 0.76 → Incerta (margin 0.04 < 0.05); cos 0.80 / 0.70 → Sicura(A); three references A, B, C with best = C by 0.1 → Sicura(C)
- AC-498 Centroid = normalized mean of the normalized reference embeddings: two references of A, (1,0) and (0.6,0.8), give a centroid ∝ (1.6, 0.8); a frase closer to that centroid than to B is Sicura(A) even when it is nearer to one single reference of B than to either single reference of A (fixture) — the centroid rule, not the max rule
- AC-499 A non-comparable frase (zero vector, NaN, infinite, dimension ≠ the references') → Incerta, never an exception; a non-comparable reference is ignored in its Parlante's centroid; a Parlante with no comparable reference is left out of the ranking
- AC-500 SIMILARITA_MINIMA = 0.30 and MARGINE_MINIMO = 0.05 are named constants defined once, marked PROVISIONAL with a KDoc pointer to ADR 0019 §4.3; SoglieSomiglianza rejects margine <= 0, NaN and minima outside [-1, 1]

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
- **tec-classificatore-somiglianza** (consumed/implemented) — owner `porte-parlanti`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `ClassificatoreSomiglianza`: interface { fun classifica(riferimenti: Map<ParlanteId, List<Impronta>>, frasi: List<Impronta>): List<Classificazione> } — output has the size and order of frasi; riferimenti has >= 2 keys, each non-empty (the caller guarantees it: require); never throws on print data (ADR 0019 §4.3)
    - `Classificazione`: sealed interface (parlanti:applicazione) { data class Sicura(val parlanteId: ParlanteId); data object Incerta } — no number
    - `SoglieSomiglianza`: data class(minima: Double, margine: Double) — require(margine > 0 && minima in -1.0..1.0), NaN rejected; injected as config; PROVISIONAL (ADR 0019 §4.3)
    - `ClassificatoreSomiglianzaFinta`: testFixtures — configurable table frase index → Classificazione; records the riferimenti map it receives
    - `Impronta`: see agg-parlante (parlanti:dominio)
  - keys (minting rules):
    - `ParlanteId`: minted by conferma-attribuzione (new Nome) and salta-voce via GeneratoreId (UUID v4) — stable across rinomina, promozione and eliminazione (tombstone keeps it); disappears only via INV-25 (occasionale left without Attribuzioni)

Sources: ADRs 0002, 0003, 0004, 0012, 0019 (.mismagent/decisions/); ADR 0019 §4.3, manifest delta 2026-09-24-semi-automatica.
