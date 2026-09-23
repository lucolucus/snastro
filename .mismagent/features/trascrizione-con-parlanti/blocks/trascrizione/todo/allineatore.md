---
id: "allineatore"
type: "adapter"
context: "trascrizione"
side: "app"
wave: 11
release: "R1"
module: ":trascrizione:adattatori (..ml)"
consumes:
  - "kernel-pl"
  - "tec-riconoscitore"
  - "tec-vad"
  - "tec-allineatore"
depends_on: []
related_adrs:
  - "0002"
  - "0003"
  - "0004"
  - "0012"
  - "0013"
  - "0014"
  - "0015"
gated_by:
  - "ADR closing spike allineamento-parole-voci — satisfied: ADR 0015 (accepted 2026-09-24)"
---
# allineatore — Allineatore (Kotlin puro) — strategia A, trascrizione per turno (ADR 0015)

## What to do
Implement Allineatore with strategy A (ADR 0015): transcribe per merged turn over the RiconoscitoreParlato + Vad ports — merge same-voice turns closer than 1 s, drop merged turns < 500 ms, split turns > 25 s with the Vad (no ASR call > 25 s, none < 200 ms), one SegmentoGrezzo per merged turn with the turn's interval, drop empty text, output sorted by (inizio, voceIndice, fine); pure Kotlin, testable in the gate with fakes.

Note: AMENDED 2026-09-24 (ADR 0015, manifest delta 2026-09-24-allineamento): strategy A (transcribe per merged turn), rules 1-10 of ADR 0015; DURATA_MINIMA_TURNO_MS = 500 confirmed by the user 2026-09-24 (re-measure trigger in ADR 0015 Consequences may tune it by amendment). The recognizer is loaded once per Elaborazione by riconoscitore-sherpa (AC-388), not here. The tec-allineatore port is unchanged.

## Tasks
- AC-246 AllineatoreContratto passa contro l'implementazione con RiconoscitoreParlato e Vad finti (nel gate)
- AC-247 Turni sovrapposti producono Segmenti sovrapposti, entrambi conservati
- AC-248 (REWRITTEN 2026-09-24, ADR 0015 regola 3) Un turno unito più lungo di 25 s è passato a Vad.parlato sui suoi soli campioni e sono riconosciuti solo gli intervalli restituiti; nessuna chiamata a RiconoscitoreParlato riceve più di 25 000 ms di audio, qualunque cosa restituisca il Vad (un intervallo Vad > 25 s è tagliato in pezzi consecutivi uguali, ciascuno <= 25 s); un turno <= 25 s è riconosciuto intero, senza Vad
- AC-379 (ADR 0015 regola 1) Unione dei turni: ordinati per (inizio, voceIndice, fine), un turno si unisce al turno unito precedente solo se è l'immediato precedente in quell'ordine, ha lo stesso voceIndice e inizio − fine_precedente < 1000 ms (sovrapposto = distanza negativa → unito); intervallo unito = (inizio del primo, max fine); una voce diversa in mezzo impedisce l'unione; turni di voci diverse non sono mai uniti, tagliati o eliminati perché sovrapposti (test a tabella: distanza 999 ms → unito, 1000 ms → separato)
- AC-380 (ADR 0015 regola 2) Un turno unito più corto di DURATA_MINIMA_TURNO_MS = 500 ms non produce alcuna chiamata ASR né alcun SegmentoGrezzo e non è unito a un vicino (499 ms → scartato, 500 ms → riconosciuto; verifica sulle chiamate del RiconoscitoreParlato finto)
- AC-381 (ADR 0015 regola 4) Un intervallo Vad più corto di 200 ms non produce alcuna chiamata ASR (199 ms → nessuna chiamata, 200 ms → chiamata)
- AC-382 (ADR 0015 regola 7) Un turno il cui testo unito è vuoto o di soli spazi non produce alcun SegmentoGrezzo; se tutti i turni sono vuoti o corti il risultato è una lista vuota, senza eccezioni
- AC-383 (ADR 0015 regole 5, 6, 8) Un SegmentoGrezzo per turno unito, con il voceIndice del turno e l'intervallo del TURNO UNITO anche quando è stato spezzato dal Vad; il testo è la concatenazione, con un solo spazio e in ordine di tempo, dei testi (trim) dei pezzi non vuoti; i campioni passati all'ASR sono tagliati esattamente ai limiti del turno o del pezzo (nessun padding); Riconoscimento.token è ignorato
- AC-384 (ADR 0015 regola 10) L'output è ordinato per (inizio, voceIndice, fine) ed è deterministico: lo stesso input dà sempre la stessa lista; l'Allineatore non numera Voci né Segmenti
- AC-385 (ADR 0015) Le costanti GAP_UNIONE_TURNI_MS = 1000, DURATA_MINIMA_TURNO_MS = 500, DURATA_MASSIMA_CHIAMATA_MS = 25000 e DURATA_MINIMA_CHIAMATA_MS = 200 sono valori con nome definiti una sola volta nell'adattatore (code review; nessun letterale duplicato)

## Dependencies
- **GATED — not ready until:** ADR closing spike allineamento-parole-voci — satisfied: ADR 0015 (accepted 2026-09-24)
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
- **tec-riconoscitore** (consumed/implemented) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `RiconoscitoreParlato`: interface { fun riconosci(c: CampioniAudio): Riconoscimento }
    - `Riconoscimento`: data class(testo: String, token: List<Token>?) — token null if the model gives no timestamps
    - `Token`: data class(testo: String, intervallo: IntervalloMs) — relative to the start of the given samples
- **tec-vad** (consumed/implemented) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `Vad`: interface { fun parlato(c: CampioniAudio): List<IntervalloMs> } — ordered, non-overlapping
- **tec-allineatore** (consumed/implemented) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `Allineatore`: interface { fun allinea(campioni: CampioniAudio, turni: List<Turno>): List<SegmentoGrezzo> } — the adapter is built with RiconoscitoreParlato + Vad, so strategy A or B fits the same port
    - `SegmentoGrezzo`: data class(voceIndice: Int, intervallo: IntervalloMs, testo: String) — overlaps preserved, never trimmed/dropped (INV-7, Q-4)
    - `Turno`: see tec-diarizzatore

Sources: ADRs 0002, 0003, 0004, 0012, 0013, 0014, 0015 (.mismagent/decisions/); ADR 0015 (Decision, rules 1-10), spike allineamento-parole-voci, INV-7.
