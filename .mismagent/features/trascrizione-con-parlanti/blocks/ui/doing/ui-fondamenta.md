---
id: "ui-fondamenta"
type: "ui"
context: "ui"
side: "app"
wave: 6
release: "R0"
module: ":ui (snastro.ui, snastro.ui.testi)"
consumes:
  - "kernel-pl"
depends_on: []
related_adrs:
  - "0002"
  - "0003"
  - "0010"
  - "0012"
consumes_rm: []
triggers: []
owns_boundaries:
  tec-shell-ui:
    projection: "in-process"
    contract_test: "consumer-driven"
    pinned_types:
      SessioneProgetto: "interface { val corrente: StateFlow<ProgettoAperto?>; fun crea(cartellaGenitore: String, nome: String): Esito<ProgettoAperto>; fun apri(percorso: String): Esito<ProgettoAperto>; fun chiudi() }"
      ProgettoAperto: "data class(progettoId: ProgettoId, nome: String, percorso: String)"
      ErroreSessione: "sealed interface : ErroreDominio (file ErroriSessione.kt) { NomeProgettoVuoto; CartellaNonValida; ProgettoGiaAperto; DatabasePiuRecente } — no CartellaGiaEsistente: crea derives a free folder name (AC-264), re-pinned 2026-09-23 (user decision)"
      ApriEsterno: "interface { fun apriFile(percorso: String); fun mostraNellaCartella(percorso: String) }"
      AggiornamentiVista: "interface { val cambiamenti: Flow<Cambiamento> }"
      Cambiamento: "data class(registrazioneId: RegistrazioneId?) — null = everything may have changed"
---
# ui-fondamenta — Fondamenta UI: tema, testi, shell, messaggi d'errore, porte di shell

## What to do
Derived owner (rule 11) of what every screen shares: Material 3 theme, snastro.ui.testi, MessaggiErrore.kt (one exhaustive when per context error hierarchy, R25), palette(voceId) stable by number, mm:ss and dd/MM/yyyy formatting, the app shell (left nav: Progetto selector, Registrazioni, Parlanti; no project open → S1 only), the ports SessioneProgetto, ApriEsterno, AggiornamentiVista (R15), and the render-check harness + fixtures.

Note: RELEASE PIVOT 2026-09-23 (user decision, dispatch.log (release-plan)): the shell sections are injected by the composition root (AC-341) so R0 (avvio-r0) and R1 (avvio-composizione) ship without the Parlanti section; AC-177 describes the R2 composition (avvio-parlanti). Added while the block is in doing: the composer must hand AC-341 to the in-flight worker (or land it as a follow-up before avvio-r0).

### Consumes read-models: —
### Triggers: —

## Tasks
- AC-177 La shell senza progetto aperto mostra solo S1; con un progetto aperto mostra Registrazioni e Parlanti
- AC-178 palette(voceId) restituisce sempre lo stesso colore per lo stesso numero di Voce
- AC-179 Formattazione: 75 003 ms → '01:15'; 4 503 000 ms → '75:03'; 2026-09-12 → '12/09/2026'
- AC-180 MessaggiErrore copre ogni errore di ogni contesto senza ramo else (un nuovo errore rompe la compilazione)
- AC-181 Stati della shell: caricamento del progetto (indicatore), errore di apertura (messaggio) resi dal render-check
- AC-341 (R0) La shell riceve dalla composizione l'insieme delle sezioni disponibili: se la sezione Parlanti non è fornita (release R0 e R1) la voce di navigazione 'Parlanti' NON compare e nessuna schermata Parlanti è raggiungibile; se è fornita (R2) la shell si comporta come in AC-177 — test sul presenter della shell con e senza la sezione
- (rendering — sizing/overflow/contrast/state rendering at 1280x800 and 1024x640 — is owned by realize-ui + `./gradlew :ui:renderCheck`, not a tests_nl item)

## Dependencies
- **tec-shell-ui** (OWNED here — built before its consumers) — owner `ui-fondamenta`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `SessioneProgetto`: interface { val corrente: StateFlow<ProgettoAperto?>; fun crea(cartellaGenitore: String, nome: String): Esito<ProgettoAperto>; fun apri(percorso: String): Esito<ProgettoAperto>; fun chiudi() }
    - `ProgettoAperto`: data class(progettoId: ProgettoId, nome: String, percorso: String)
    - `ErroreSessione`: sealed interface : ErroreDominio (file ErroriSessione.kt) { NomeProgettoVuoto; CartellaNonValida; ProgettoGiaAperto; DatabasePiuRecente } — no CartellaGiaEsistente: crea derives a free folder name (AC-264), re-pinned 2026-09-23 (user decision)
    - `ApriEsterno`: interface { fun apriFile(percorso: String); fun mostraNellaCartella(percorso: String) }
    - `AggiornamentiVista`: interface { val cambiamenti: Flow<Cambiamento> }
    - `Cambiamento`: data class(registrazioneId: RegistrazioneId?) — null = everything may have changed
  - keys (minting rules):
    - `percorso`: see tec-registro-progetti
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

Sources: ADRs 0002, 0003, 0010, 0012 (.mismagent/decisions/); features/trascrizione-con-parlanti/UI/ux-proposal.md App shell, dev-architecture-app.md #presenter, R8/R15.
