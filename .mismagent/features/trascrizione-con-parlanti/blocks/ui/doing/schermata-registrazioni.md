---
id: "schermata-registrazioni"
type: "ui"
context: "ui"
side: "app"
wave: 8
release: "R0"
module: ":ui (snastro.ui.registrazioni)"
consumes:
  - "kernel-pl"
  - "tec-lettore-audio"
  - "tec-shell-ui"
depends_on:
  - "registrazioni-del-progetto"
  - "stati-elaborazione"
  - "servizi-registrazione"
  - "avvia-elaborazione"
  - "lettore-audio"
  - "ui-fondamenta"
related_adrs:
  - "0002"
  - "0003"
  - "0004"
  - "0005"
  - "0010"
  - "0012"
consumes_rm:
  - "registrazioni-del-progetto"
  - "stati-elaborazione"
triggers:
  - "AggiungiRegistrazione"
  - "ModificaDataRegistrazione"
  - "AvviaElaborazione"
---
# schermata-registrazioni — S2 · Registrazioni del Progetto

## What to do
S2: the presenter joins the slices by registrazioneId (R1); drag-and-drop + file picker; per-row '▶' over the LettoreAudio port; live refresh via AggiornamentiVista; elapsed time from avviataAlle with an injected Clock. The Trascrizione sources are optional (R0 variant, AC-342): without them the row shows only titolo, data, durata and '▶'.

Note: RELEASE PIVOT 2026-09-23 (user decision, dispatch.log (release-plan)): S2 ships in R0 WITHOUT Trascrizione/Parlanti features: the Trascrizione sources (stati-elaborazione, avvia-elaborazione — both already merged, compile-time only) are optional presenter inputs, absent in R0 (AC-342) and supplied by avvio-composizione in R1. The identification badge (AC-204, Parlanti read-model identificazione-registrazioni, R2) MOVED to block schermata-registrazioni-identificazione (R2) so R0 needs no R2 block. NEW SURFACE (user decision: R0 = 'import, list and play'): a per-row '▶' over the LettoreAudio port (AC-343) — not in the original ux-proposal S2, recorded as a ux amendment. Carry (stati-elaborazione code-review): NON_AVVIATA must be rendered with an action → AC-344.

### Consumes read-models: registrazioni-del-progetto, stati-elaborazione
### Triggers: AggiungiRegistrazione, ModificaDataRegistrazione, AvviaElaborazione

## Tasks
- AC-199 Vuoto: 'Nessuna registrazione. Trascina qui un file audio'
- AC-200 Caricamento mostrato come indicatore
- AC-201 Errore di aggiunta mostrato inline, nessuna riga nuova
- AC-202 Ordinamento per data, dalla più recente
- AC-203 in_attesa → 'In coda (n)'; in_corso → 'In corso · separazione voci · 3:12' (tempo da avviataAlle, nessuna percentuale); fallita → motivo + 'Riprova' (solo su fallita); completata apre S3
- AC-205 La riga si aggiorna quando cambiano stato o fase
- AC-206 Modifica della data inline
- AC-342 (R0 variant) Le sorgenti di Trascrizione sono OPZIONALI: il presenter costruito SENZA StatiElaborazione e AvviaElaborazione (R0, avvio-r0) mostra per ogni riga solo titolo, data (modificabile, AC-206), durata e '▶' (AC-343); nessuna colonna di stato, nessun 'Riprova'/'Trascrivi', nessun badge, e il click su una riga non apre S3 — test del presenter con le sole finte di RegistrazioniDelProgetto, AggiungiRegistrazione, ModificaDataRegistrazione e LettoreAudio; con le sorgenti fornite (R1, avvio-composizione) valgono AC-203/AC-205/AC-344
- AC-343 (R0) Ogni riga ha '▶' che riproduce la Registrazione dall'inizio via LettoreAudio.riproduciDa(id, 0); durante la riproduzione della riga il controllo diventa pausa (StatoLettore.registrazioneId = la riga), '▶' su un'altra riga sostituisce la riproduzione in corso; LettoreAudio.disponibile(id) = false → '▶' disabilitato con 'Audio non disponibile'
- AC-344 (R1) Con le sorgenti di Trascrizione fornite, una Registrazione senza Elaborazione (StatoElaborazioneVista.NON_AVVIATA — es. importata in R0) mostra 'Trascrivi' che invoca AvviaElaborazione; un errore del comando è mostrato inline sulla riga e nulla cambia
- (rendering — sizing/overflow/contrast/state rendering at 1280x800 and 1024x640 — is owned by realize-ui + `./gradlew :ui:renderCheck`, not a tests_nl item)

## Dependencies
- Blocks built first: `registrazioni-del-progetto` (wave 5), `stati-elaborazione` (wave 5), `servizi-registrazione` (wave 4), `avvia-elaborazione` (wave 4), `lettore-audio` (wave 7), `ui-fondamenta` (wave 6)
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

Sources: ADRs 0002, 0003, 0004, 0005, 0010, 0012 (.mismagent/decisions/); features/trascrizione-con-parlanti/UI/ux-proposal.md S2 (+ R1).
