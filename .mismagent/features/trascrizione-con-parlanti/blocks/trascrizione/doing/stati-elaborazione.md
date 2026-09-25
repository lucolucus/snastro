---
id: "stati-elaborazione"
type: "read-model"
context: "trascrizione"
side: "app"
wave: 5
release: "R1"
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
  - "0014"
  - "0018"
  - "0019"
  - "0020"
view_shape:
  stati: "List<{registrazioneId, stato: StatoElaborazioneVista, fase: FaseElaborazione?, avviataAlle: Instant?, motivoFallimento: String?, posizioneInCoda: Int?, numVoci: Int?, numeroPersone: Int?, trascrittoDisponibile: Boolean, elaborazioneId: ElaborazioneId?}>"
view_sources:
  stati: "stato ← latest Elaborazione (mapped via named predicates to StatoElaborazioneVista); fase ← in-memory FasiInCorso implementing SegnalatoreFase; avviataAlle, motivoFallimento ← Elaborazione; posizioneInCoda ← rank among ElaborazioneRepository.inAttesa (FIFO); numVoci ← Trascritto (non-null iff trascrittoDisponibile, whatever the latest run's state); numeroPersone ← latest Elaborazione.numeroPersone?.valore (write-path input of AvviaElaborazione, agg-elaborazione; null when absent or NON_AVVIATA); trascrittoDisponibile ← TrascrittoRepository (a Trascritto exists for the Registrazione, ADR 0018); elaborazioneId ← latest Elaborazione.id (agg-elaborazione; null for NON_AVVIATA; the id AnnullaElaborazione takes)"
---
# stati-elaborazione — StatiElaborazione — fetta Trascrizione (S2) + FasiInCorso

## What to do
Trascrizione slice of S2 (R1 split) + the in-memory FasiInCorso holder (implements SegnalatoreFase; wired by :avvio).

REWORK 2026-09-24 (ADR 0014): each stati item gains numeroPersone: Int? = the latest Elaborazione's numeroPersone?.valore (null when absent or NON_AVVIATA); AC-162 test extended.

REWORK 2026-09-24 (ADR 0018): each item gains trascrittoDisponibile and elaborazioneId; numVoci follows the Trascritto whatever the latest state (AC-165 reworded); table tests AC-447, AC-474.

Note: AMENDED 2026-09-24 (ADR 0014): view_shape gains numeroPersone (AC-162 rewritten). AMENDED 2026-09-24 (ADR 0018 + Amendment 2026-09-24 (b), manifest delta 2026-09-24-ritrascrivi): view_shape gains trascrittoDisponibile (the S2/S3 'Ritrascrizione …' states and the S3 read-only flag derive from the pair stato + trascrittoDisponibile; no new enum value) and elaborazioneId (for S2 'Annulla'); numVoci follows the Trascritto, not the latest state.

### view_shape (field ← source)
- `stati`: List<{registrazioneId, stato: StatoElaborazioneVista, fase: FaseElaborazione?, avviataAlle: Instant?, motivoFallimento: String?, posizioneInCoda: Int?, numVoci: Int?, numeroPersone: Int?, trascrittoDisponibile: Boolean, elaborazioneId: ElaborazioneId?}> ← stato ← latest Elaborazione (mapped via named predicates to StatoElaborazioneVista); fase ← in-memory FasiInCorso implementing SegnalatoreFase; avviataAlle, motivoFallimento ← Elaborazione; posizioneInCoda ← rank among ElaborazioneRepository.inAttesa (FIFO); numVoci ← Trascritto (non-null iff trascrittoDisponibile, whatever the latest run's state); numeroPersone ← latest Elaborazione.numeroPersone?.valore (write-path input of AvviaElaborazione, agg-elaborazione; null when absent or NON_AVVIATA); trascrittoDisponibile ← TrascrittoRepository (a Trascritto exists for the Registrazione, ADR 0018); elaborazioneId ← latest Elaborazione.id (agg-elaborazione; null for NON_AVVIATA; the id AnnullaElaborazione takes)

## Tasks
- AC-162 La vista espone stato, fase, avviataAlle, motivoFallimento, posizioneInCoda, numVoci e numeroPersone per Registrazione (numeroPersone = quello dell'ultima Elaborazione, null se assente o se NON_AVVIATA; serve a precompilare 'Riprova', AC-376) — REWRITTEN 2026-09-24 (ADR 0014)
- AC-163 posizioneInCoda segue l'ordine FIFO delle in_attesa (1 = la prossima)
- AC-164 fase è presente solo per in_corso e riflette l'ultima fase segnalata; scompare quando l'Elaborazione termina
- AC-165 (REWORDED 2026-09-24, ADR 0018) numVoci = the Trascritto's Voci count whenever a Trascritto exists, null otherwise
- AC-447 Table test: stato, posizioneInCoda, fase, motivoFallimento and numeroPersone always come from the LATEST Elaborazione (AC-162). History (creation order) → stato / trascrittoDisponibile / numVoci: completata → COMPLETATA / true / of the Trascritto; completata, in_attesa → IN_ATTESA (posizione n) / true / old count; completata, in_corso → IN_CORSO (fase) / true / old count; completata, fallita → FALLITA (motivo) / true / old count; fallita → FALLITA / false / null; completata, completata → COMPLETATA / true / new count; none → NON_AVVIATA / false / —
- AC-474 elaborazioneId = the latest Elaborazione's id (null for NON_AVVIATA). After a cancellation: (in_attesa cancelled) → NON_AVVIATA, trascrittoDisponibile false, elaborazioneId null; (completata, in_attesa cancelled) → COMPLETATA, true, old numVoci, the completata's id; (fallita, in_attesa cancelled) → FALLITA with its motivo; the posizioneInCoda of the remaining queued rows is renumbered 1..n

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
- **agg-elaborazione** (consumed/implemented) — owner `elaborazione`, projection in-process, contract_test **invariant-test**
  - pinned types:
    - `Elaborazione.accoda`: (id: ElaborazioneId, registrazioneId, creataAlle: Instant, numeroPersone: NumeroPersone?): Creato<Elaborazione, ElaborazioneAccodata> — numeroPersone fixed at creation (may be absent), immutable (ADR 0014)
    - `Elaborazione.numeroPersone`: NumeroPersone? — read-only accessor; set only by accoda (and by the persistence reconstitution); no transition changes it
    - `NumeroPersone`: @JvmInline value class(valore: Int) in :trascrizione:dominio — 1..10 inclusive; factory NumeroPersone.di(n: Int): Esito<NumeroPersone> → Errore(NumeroPersoneFuoriIntervallo) outside 1..10 (sealed ErroreTrascrizione, ErroriTrascrizione.kt); the only way to build one (ADR 0014)
    - `Elaborazione.avvia`: (alle: Instant): Esito<ElaborazioneAvviata>
    - `Elaborazione.completa`: (): Esito<ElaborazioneCompletata>
    - `Elaborazione.fallisci`: (motivo: String): Esito<ElaborazioneFallita>
    - `Elaborazione.annulla`: (): Esito<ElaborazioneAnnullata> — Ok ONLY from in_attesa (domain event ElaborazioneAnnullata(id, registrazioneId), state unchanged: a check, not a transition — the repository then deletes the never-started row, ADR 0018 Amendment (b)); any other state → Errore(ElaborazioneGiaAvviata(id))
    - `named predicates`: aperta (in_attesa|in_corso), inAttesa, completata, fallita, terminale — never compare StatoElaborazione outside the aggregate
    - `errors (ErroreTrascrizione, ErroriTrascrizione.kt)`: ElaborazioneGiaAperta(registrazioneId); ElaborazioneGiaAvviata(elaborazioneId: ElaborazioneId); ElaborazioneNonTrovata(elaborazioneId: ElaborazioneId); NumeroPersoneFuoriIntervallo(valore) — ElaborazioneGiaCompletata is DELETED (ADR 0018)
  - keys (minting rules):
    - `ElaborazioneId`: minted by avvia-elaborazione via GeneratoreId (UUID v4) — internal, never crosses a context boundary
  - §14 gates (must stay green):
    - `! grep -rnE --include='*.kt' --exclude-dir=build '\b(elaborazioneQueries)\b' . | grep -vE '^\./(persistenza/|trascrizione/adattatori/src/[A-Za-z]+/kotlin/snastro/trascrizione/adattatori/persistenza/)' | grep -q .`
    - `! grep -rnE --include='*.kt' --exclude-dir=build 'StatoElaborazione\.' . | grep -E '^\./[^:]*/src/main/' | grep -vE '^\./trascrizione/(dominio/|adattatori/src/[A-Za-z]+/kotlin/snastro/trascrizione/adattatori/persistenza/)' | grep -q .`
- **agg-trascritto** (consumed/implemented) — owner `trascritto`, projection in-process, contract_test **invariant-test**
  - pinned types:
    - `Trascritto.crea`: (registrazioneId, durataMs: Long, segmenti: List<SegmentoIniziale>): Esito<Creato<Trascritto, TrascrittoCreato>> — Errore(NessunParlatoRilevato) on empty input
    - `SegmentoIniziale`: data class(voceIndice: Int, intervallo: IntervalloMs, testo: String) — trascrizione:dominio input VO
    - `Trascritto.unisci`: (sopravvive: VoceId, rimossa: VoceId): Esito<VociUnite> — keeps every confermato flag
    - `Trascritto.dividi`: (origine: VoceId, segmenti: Set<SegmentoId>): Esito<VoceDivisa> — sets confermato = true on every Segmento of S (INV-26, ADR 0019)
    - `Trascritto.riassegna`: (segmento: SegmentoId, destinazione: VoceId?): Esito<SegmentoRiassegnato> — null = a NEW Voce; the event carries a (the destination); sets confermato = true on the moved Segmento (INV-26, ADR 0019)
    - `Trascritto.riassegnaInBlocco`: (spostamenti: List<SpostamentoSegmento>): Esito<List<SegmentoRiassegnato>> — every entry validated against the PRE-batch state, moves applied in list order, then the Voci empty at the END of the batch removed (INV-6; a Voce emptied and refilled within the batch is kept); never creates a Voce, never changes a flag (INV-26); any stale entry (Segmento missing / not on da / interval differs / a missing / Segmento confermato) → Errore(TrascrittoCambiato(registrazioneId)); a == da or a duplicated segmentoId → Errore(RiassegnazioneNonAmmessa); a refusal leaves the state unchanged; empty list → Ok(emptyList()); events one per move in list order, aNuova = false, daRimossa = true on the LAST move out of each Voce empty at the end (ADR 0019 §4.5 + Amendment (b).1)
    - `SpostamentoSegmento`: data class(segmentoId: SegmentoId, da: VoceId, a: VoceId, intervallo: IntervalloMs) — trascrizione:dominio input VO; intervallo = the Segmento's interval when planned (stale guard, also against a Ritrascrivi generation swap)
    - `Trascritto.confermaSegmento`: (segmento: SegmentoId, confermato: Boolean): Esito<SegmentoConfermato?> — the same value → Ok(null), no change; unknown segment → Errore(SegmentoNonTrovato) (INV-26)
    - `Segmento.confermato`: Boolean, read-only — true iff an explicit user act placed or confirmed the Segmento on its current Voce and no ConfermaSegmento(false) revoked it (INV-26); crea starts every flag at false
    - `errors (ErroreTrascrizione, ErroriTrascrizione.kt)`: + TrascrittoCambiato(registrazioneId: RegistrazioneId) — ONE sweep owned by the trascritto rework, :ui MessaggiErrore included (ADR 0019 §4.5)
    - `read accessors`: voci: List<Voce>, segmenti: List<Segmento> (read-only copies)
  - keys (minting rules):
    - `VoceId`: minted by the trascritto aggregate from its persisted counter prossimaVoce — at creation 1..n in order of FIRST APPEARANCE (smallest turn inizioMs, tie: diarizer voceIndice); DividiVoce / riassegna-to-new take prossimaVoce++; never reused, never renumbered, == the n of the label 'Voce n'; stable for the life of one Trascritto GENERATION: a Ritrascrivi replacement (ADR 0018) is a fresh Trascritto.crea numbered from 1 again, and every VoceRef-keyed Parlanti row of the old generation is purged in the same transaction (TrascrittoSostituito)
    - `SegmentoId`: minted by the trascritto aggregate at creation only, 1..m in order (inizioMs, then voceId); no Segmento is ever created afterwards (INV-8) — stable for the Trascritto generation's life (ADR 0018: a replacement renumbers from 1)
  - §14 gates (must stay green):
    - `! grep -rnE --include='*.kt' --exclude-dir=build '\b(trascrittoQueries|voceQueries|segmentoQueries)\b' . | grep -vE '^\./(persistenza/|trascrizione/adattatori/src/[A-Za-z]+/kotlin/snastro/trascrizione/adattatori/persistenza/)' | grep -q .`
- **repo-trascrizione** (consumed/implemented) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `ElaborazioneRepository`: interface { diRegistrazione(id: RegistrazioneId): List<Elaborazione>; inAttesa(): List<Elaborazione> /* FIFO by creataAlle, tie id */; inCorso(): List<Elaborazione>; trova(id: ElaborazioneId): Elaborazione?; salva(e: Elaborazione): Esito<Unit> /* Errore(ElaborazioneGiaAperta) only: another open Elaborazione of the same Registrazione while this one is open (index elaborazione_aperta_unica); several completata are allowed (ADR 0018) */; rimuoviInAttesa(id: ElaborazioneId): Esito<Unit> /* compare-and-delete (ADR 0018 Amendment (b)): deletes the row iff it exists and is still in_attesa; started → Errore(ElaborazioneGiaAvviata); absent → Errore(ElaborazioneNonTrovata); the only deletion of an Elaborazione (amended 2026-09-25: plus rimuoviDiRegistrazione, ADR 0020) */; rimuoviDiRegistrazione(id: RegistrazioneId) /* deletes EVERY Elaborazione of the Registrazione, any state; used only by the elimination policy after its veto (ADR 0020) */ }
    - `TrascrittoRepository`: interface { trova(id: RegistrazioneId): Trascritto?; conTrascritto(): List<RegistrazioneId>; salva(t: Trascritto); rimuovi(id: RegistrazioneId) /* deletes segmento, voce and trascritto rows of the Registrazione in the caller's transaction; absent = no-op (ADR 0020) */ } — persists prossimaVoce / prossimoSegmento; salva over an existing Trascritto REPLACES it whole (Voci, Segmenti, counters: ADR 0018 replacement)
- **tec-segnalatore-fase** (consumed/implemented) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `SegnalatoreFase`: interface { fun fase(id: RegistrazioneId, f: FaseElaborazione); fun terminata(id: RegistrazioneId) }
    - `FaseElaborazione`: enum DECODIFICA | DIARIZZAZIONE | TRASCRIZIONE | ALLINEAMENTO (trascrizione:applicazione) — progress only, not guarded state

Sources: ADRs 0002, 0003, 0004, 0006, 0007, 0012, 0014, 0018, 0019, 0020 (.mismagent/decisions/); features/trascrizione-con-parlanti/UI/ux-proposal.md S2 (+ R1, amendment 2026-09-24), architecture.md § Pipeline, ADR 0014.
