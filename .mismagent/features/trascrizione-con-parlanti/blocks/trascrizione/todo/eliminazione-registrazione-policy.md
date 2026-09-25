---
id: "eliminazione-registrazione-policy"
type: "application-service"
context: "trascrizione"
side: "app"
wave: 4
release: "R2"
module: ":trascrizione:applicazione (..politiche)"
consumes:
  - "kernel-pl"
  - "agg-elaborazione"
  - "repo-trascrizione"
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
  - "0020"
commands:
  - "ApplicaEliminazioneRegistrazione"
invariants:
  - "INV-28 (Trascrizione half) the elimination is vetoed while an Elaborazione of the Registrazione is in_attesa | in_corso (re-read INSIDE the deleting transaction); otherwise every Elaborazione (any state) and the Trascritto (Voci, Segmenti) of the Registrazione are removed in that transaction"
---
# eliminazione-registrazione-policy — Policy di eliminazione della Registrazione (veto se un'Elaborazione è aperta, altrimenti purga Trascritto ed Elaborazioni)

## What to do
ApplicaEliminazioneRegistrazionePolitica(elaborazioni, trascritti).applica(registrazioneId): the Trascrizione half of INV-28, run synchronously inside the deleting transaction. Re-read diRegistrazione(id): any Elaborazione aperta (named predicate) → Errore(ElaborazioneGiaAperta(id)) and nothing removed; otherwise trascritti.rimuovi(id) + elaborazioni.rimuoviDiRegistrazione(id). Structural only: never decodes or extracts.

Note: NEW 2026-09-25 (ADR 0020 §2 step 4, manifest delta 2026-09-25-elimina-registrazione; wave 4, the delta's 'wave 18' corrected at fold): ApplicaEliminazioneRegistrazionePolitica(elaborazioni: ElaborazioneRepository, trascritti: TrascrittoRepository).applica(registrazioneId): Esito<Unit>. Re-reads elaborazioni.diRegistrazione(id) inside the transaction and vetoes via the aggregate's named predicate aperta (never StatoElaborazione outside the aggregate, agg-elaborazione §14 gate); then trascritti.rimuovi(id) + elaborazioni.rimuoviDiRegistrazione(id). ElaborazioneGiaAperta is an EXISTING variant: no ErroreTrascrizione sweep. Structural only: never decodes or extracts. Invoked by abbonato-eliminazione-trascrizione (synchronous). Release-neutral code, built for R2. RESOLVED 2026-09-25 (user checkpoint 2026-09-25): Q-3 — ADR 0012 Amendment 2026-09-25 (d) widens the enforced_by to :trascrizione:applicazione politiche (validated on the tree, exit 0, and on fixtures); AC-611 is no longer by-construction.

### Invariants owned here (one test each, name starts with the tag)
- INV-28 (Trascrizione half) the elimination is vetoed while an Elaborazione of the Registrazione is in_attesa | in_corso (re-read INSIDE the deleting transaction); otherwise every Elaborazione (any state) and the Trascritto (Voci, Segmenti) of the Registrazione are removed in that transaction

## Tasks
- AC-608 (INV-28) Veto: with an in_attesa or an in_corso Elaborazione for r (in each case also alongside a completata one) → Errore(ElaborazioneGiaAperta(r)); nothing is removed: rimuovi / rimuoviDiRegistrazione are never called (counting finte)
- AC-609 (INV-28) Purge: with completata + fallita Elaborazioni and a Trascritto → Ok; afterwards diRegistrazione(r) is empty and trova(r) is null; the Elaborazioni and Trascritto of another Registrazione are unchanged
- AC-610 A Registrazione with no Elaborazione (NON_AVVIATA) and no Trascritto → Ok, with no call that removes anything
- AC-611 EstrattoreImprontaFinta / DecodificatoreAudioFinta (either context's) are never collaborators (none injected; constructor test), and the ADR 0012 (b) prohibition grep — widened by ADR 0012 Amendment 2026-09-25 (d) to trascrizione/applicazione/src/main (incl. snastro.trascrizione.applicazione.porte.DecodificatoreAudio) — stays green on this block's ..politiche package (falsifiable: an import of DecodificatoreAudio / CampioniAudio there turns it red)

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
- **repo-trascrizione** (consumed/implemented) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `ElaborazioneRepository`: interface { diRegistrazione(id: RegistrazioneId): List<Elaborazione>; inAttesa(): List<Elaborazione> /* FIFO by creataAlle, tie id */; inCorso(): List<Elaborazione>; trova(id: ElaborazioneId): Elaborazione?; salva(e: Elaborazione): Esito<Unit> /* Errore(ElaborazioneGiaAperta) only: another open Elaborazione of the same Registrazione while this one is open (index elaborazione_aperta_unica); several completata are allowed (ADR 0018) */; rimuoviInAttesa(id: ElaborazioneId): Esito<Unit> /* compare-and-delete (ADR 0018 Amendment (b)): deletes the row iff it exists and is still in_attesa; started → Errore(ElaborazioneGiaAvviata); absent → Errore(ElaborazioneNonTrovata); the only deletion of an Elaborazione (amended 2026-09-25: plus rimuoviDiRegistrazione, ADR 0020) */; rimuoviDiRegistrazione(id: RegistrazioneId) /* deletes EVERY Elaborazione of the Registrazione, any state; used only by the elimination policy after its veto (ADR 0020) */ }
    - `TrascrittoRepository`: interface { trova(id: RegistrazioneId): Trascritto?; conTrascritto(): List<RegistrazioneId>; salva(t: Trascritto); rimuovi(id: RegistrazioneId) /* deletes segmento, voce and trascritto rows of the Registrazione in the caller's transaction; absent = no-op (ADR 0020) */ } — persists prossimaVoce / prossimoSegmento; salva over an existing Trascritto REPLACES it whole (Voci, Segmenti, counters: ADR 0018 replacement)

Sources: ADRs 0002, 0003, 0004, 0006, 0007, 0012, 0014, 0018, 0020 (.mismagent/decisions/); ADR 0007/0012, ADR 0020 §2/§7, features/trascrizione-con-parlanti/tactical-model.md § Amendment 2026-09-25 (ADR 0020), manifest delta 2026-09-25-elimina-registrazione.
