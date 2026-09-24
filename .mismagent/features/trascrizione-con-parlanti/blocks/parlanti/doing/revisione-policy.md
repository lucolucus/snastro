---
id: "revisione-policy"
type: "application-service"
context: "parlanti"
side: "app"
wave: 4
release: "R2"
module: ":parlanti:applicazione (..politiche)"
consumes:
  - "kernel-pl"
  - "agg-parlante"
  - "agg-attribuzione"
  - "repo-parlanti"
  - "voci-per-parlanti"
  - "eventi-revisione"
depends_on: []
related_adrs:
  - "0002"
  - "0003"
  - "0006"
  - "0007"
  - "0009"
  - "0012"
model_hint: "deep"
commands:
  - "ApplicaRevisione"
invariants:
  - "INV-21 after a Revisione (STRUCTURAL part only, inside the Revisione's transaction — ADR 0012 Amendment (b) point 3): a removed Voce loses Attribuzione + derived print; a surviving/changed attributed Voce KEEPS its print row in the Revisione's transaction (possibly stale) and is re-derived AFTER commit by RiallineaImpronte; a NEW Voce starts without Attribuzione; in unire(A,B) with different Parlanti A's Attribuzione wins (B's Attribuzione and row go); same Parlante → A keeps its own row (stale), B's row and Attribuzione go; EXCEPTION (user decision 2026-09-23): in unire(A,B) with B attributed to P and A unattributed, A INHERITS the Attribuzione to P — B's Attribuzione is re-keyed to A (Attribuzione.trasferisci) and B's row of P is re-keyed to A in-transaction (Parlante.trasferisciImpronta, keeping its sorgente, so stale) and refreshed after commit; if P is eliminato only the tombstone Attribuzione is re-keyed, no row (policy-only exception to INV-13/INV-17) — so P is not left without Attribuzioni and INV-25 does not fire. The policy NEVER decodes nor extracts (ADR 0012 enforced_by)"
  - "INV-25 a Parlante left without Attribuzioni: occasionale ceases to exist; ricorrente is kept"
---
# revisione-policy — Policy di Revisione dei Parlanti

## What to do
ApplicaRevisione(evento) reacts to VociUnite / VoceDivisa / SegmentoRiassegnato inside the Revisione's transaction (called by abbonato-revisione-parlanti) — STRUCTURAL part of INV-21/INV-25 only: remove Attribuzione + row of a removed/emptied Voce, unire precedence and inheritance by re-keying (Attribuzione.trasferisci, Parlante.trasferisciImpronta; eliminato P → Attribuzione only), new Voci unattributed, cease an orphan occasionale. Never decodes nor extracts; stale rows are refreshed after commit by riallinea-impronte.

Note: AMENDED 2026-09-23 (ADR 0012 Amendment (b) points 3-4, user option (c)): structural part only; print freshness moved to riallinea-impronte (after commit, abbonato-riallineamento-impronte). ADR 0012 enforced_by (no EstrattoreImpronta/DecodificatoreAudio/CampioniAudio/Impronta under politiche/) is red on the in-flight branch (c2f1b72/af0540d import them) BY DESIGN — green once this rework lands. Uses Attribuzione.trasferisci + Parlante.trasferisciImpronta (follow-ups of attribuzione/parlante) for the inheritance.

### Invariants owned here (one test each, name starts with the tag)
- INV-21 after a Revisione (STRUCTURAL part only, inside the Revisione's transaction — ADR 0012 Amendment (b) point 3): a removed Voce loses Attribuzione + derived print; a surviving/changed attributed Voce KEEPS its print row in the Revisione's transaction (possibly stale) and is re-derived AFTER commit by RiallineaImpronte; a NEW Voce starts without Attribuzione; in unire(A,B) with different Parlanti A's Attribuzione wins (B's Attribuzione and row go); same Parlante → A keeps its own row (stale), B's row and Attribuzione go; EXCEPTION (user decision 2026-09-23): in unire(A,B) with B attributed to P and A unattributed, A INHERITS the Attribuzione to P — B's Attribuzione is re-keyed to A (Attribuzione.trasferisci) and B's row of P is re-keyed to A in-transaction (Parlante.trasferisciImpronta, keeping its sorgente, so stale) and refreshed after commit; if P is eliminato only the tombstone Attribuzione is re-keyed, no row (policy-only exception to INV-13/INV-17) — so P is not left without Attribuzioni and INV-25 does not fire. The policy NEVER decodes nor extracts (ADR 0012 enforced_by)
- INV-25 a Parlante left without Attribuzioni: occasionale ceases to exist; ricorrente is kept

## Tasks
- INV-21 unire(A, B) con A e B attribuiti a Parlanti diversi → vince A; l'Attribuzione di B e l'impronta derivata da B sono cancellate
- INV-21 unire con A attribuita e B no → A mantiene la sua riga d'impronta (ora obsoleta: la sorgente non corrisponde più), nessuna estrazione nella transazione; RiallineaImpronte la aggiorna dopo il commit
- INV-21 unire(A, B) con B attribuita a P attivo e A non attribuita → l'Attribuzione di B è ri-chiavata su A (Attribuzione(A) = P, nessuna Attribuzione per B) e la riga d'impronta di P per B è ri-chiavata su A conservando la sua sorgente_impronta (quindi obsoleta), aggiornata dopo il commit da RiallineaImpronte; P occasionale NON cessa (INV-25 non scatta)
- INV-21 idem con P eliminato → solo l'Attribuzione (tombstone) è ri-chiavata su A; nessuna riga d'impronta è creata (P resta a zero impronte, INV-13)
- INV-21 unire(A, B) con A e B attribuiti allo stesso Parlante P → A mantiene l'Attribuzione a P e la propria riga (obsoleta, aggiornata dopo il commit); l'Attribuzione e la riga di B sono cancellate; P non cessa
- INV-21 dividere(A, S) → A' nasce senza Attribuzione; A mantiene l'Attribuzione e la propria riga d'impronta (obsoleta, aggiornata dopo il commit)
- INV-21 riassegnare → la sorgente svuotata perde Attribuzione e impronta; una destinazione nuova nasce senza Attribuzione
- INV-25 un occasionale rimasto senza Attribuzioni cessa; un ricorrente resta
- AC-96 Un errore della policy (es. il repository della Finta fallisce) restituisce Errore e annulla anche la Revisione
- AC-291 La policy non decodifica né estrae: nessun riferimento a EstrattoreImpronta, DecodificatoreAudio, CampioniAudio o Impronta nel package politiche (enforced_by di ADR 0012 verde) e nessuna riga d'impronta è creata o ri-estratta dalla policy (le Finte ML non sono nemmeno collegate)

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
- **agg-parlante** (consumed/implemented) — owner `parlante`, projection in-process, contract_test **invariant-test**
  - pinned types:
    - `Parlante.crea`: (id, progettoId, nome: Nome, tipo: TipoParlante): Creato<Parlante, ParlanteCreato>
    - `Parlante.rinomina`: (nome: Nome): Esito<ParlanteRinominato>
    - `Parlante.promuovi`: (nome: Nome?): Esito<ParlantePromosso>
    - `Parlante.elimina`: (): Esito<ParlanteEliminato> — purges every ImprontaVocale in the same call
    - `Parlante.registraImpronta`: (voceRef: VoceRef, impronta: Impronta, sorgente: String /* SorgenteImpronta.chiave */, modello: String /* EstrattoreImpronta.modello */): Esito<Unit> — insert or replace the ONE print of that VoceRef, never touches others
    - `Parlante.trasferisciImpronta`: (da: VoceRef, a: VoceRef): Unit — policy-only (INV-21 unire inheritance/re-keying): moves the print of `da` to key `a` keeping impronta, sorgente and modello (so it is stale by construction, refreshed after commit by RiallineaImpronte); no-op when there is no print for `da` (always so for an eliminato); require no print already present for `a`
    - `ImprontaVocale`: entity data class(voceRef: VoceRef, impronta: Impronta, sorgente: String, modello: String) in parlanti:dominio, exposed read-only via Parlante.impronte: List<ImprontaVocale>; fun obsoleta(chiaveCorrente: String, modelloCorrente: String): Boolean = sorgente != chiaveCorrente || modello != modelloCorrente (the ONE staleness rule, ADR 0012 (b)); same rule as companion ImprontaVocale.obsoleta(sorgente, modello, chiaveCorrente, modelloCorrente) for callers holding only row metadata
    - `Parlante.rimuoviImpronta`: (voceRef: VoceRef): Unit
    - `Nome`: smart-constructor VO: Nome.di(String): Esito<Nome>; normalizzato = trim().lowercase(Locale.ROOT)
    - `Impronta`: class(valori: FloatArray) in parlanti:dominio, explicit equals/hashCode
    - `named predicates`: attivo, eliminato, occasionale, haImpronte
  - keys (minting rules):
    - `ParlanteId`: minted by conferma-attribuzione (new Nome) and salta-voce via GeneratoreId (UUID v4) — stable across rinomina, promozione and eliminazione (tombstone keeps it); disappears only via INV-25 (occasionale left without Attribuzioni)
    - `nome_normalizzato`: minted by the Nome VO: trim().lowercase(Locale.ROOT); never computed in SQL (ADR 0007)
  - §14 gates (must stay green):
    - `! grep -rnE --include='*.kt' --exclude-dir=build '\b(parlanteQueries|improntaVocaleQueries)\b' . | grep -vE '^\./(persistenza/|parlanti/adattatori/src/[A-Za-z]+/kotlin/snastro/parlanti/adattatori/persistenza/)' | grep -q .`
    - `! grep -rnE --include='*.kt' --exclude-dir=build 'StatoParlante\.' . | grep -E '^\./[^:]*/src/main/' | grep -vE '^\./parlanti/(dominio/|adattatori/src/[A-Za-z]+/kotlin/snastro/parlanti/adattatori/persistenza/)' | grep -q .`
- **agg-attribuzione** (consumed/implemented) — owner `attribuzione`, projection in-process, contract_test **invariant-test**
  - pinned types:
    - `Attribuzione.conferma`: (voceRef, progettoId, parlanteId): Creato<Attribuzione, AttribuzioneConfermata>
    - `Attribuzione.cambia`: (parlanteId): Esito<AttribuzioneConfermata?> — same parlante = Ok(null), no event
    - `Attribuzione.trasferisci`: (a: VoceRef): Attribuzione — POLICY-ONLY re-keying (INV-21 unire inheritance, ADR 0012 Amendment (b) point 4): same parlanteId and progettoId, key = a; require a.registrazioneId == voceRef.registrazioneId; checks NO Parlante state (valid for an eliminato tombstone — the explicit INV-13/INV-17 exception); emits no event (consumers are reached via VociUnite); never used by ConfermaAttribuzione / SaltaVoce
  - keys (minting rules):
    - `VoceRef`: composite (registrazioneId, voceId), typed kernel VO because >=2 contexts use it — correlation key of Attribuzione, ImprontaVocale and the Documento name map; stable as its parts
  - §14 gates (must stay green):
    - `! grep -rnE --include='*.kt' '\.trasferisci(Impronta)?\(' parlanti/applicazione/src/main | grep '/comandi/' | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\*|/\*)' | grep -q .`
    - `! grep -rnE --include='*.kt' --exclude-dir=build '\b(attribuzioneQueries)\b' . | grep -vE '^\./(persistenza/|parlanti/adattatori/src/[A-Za-z]+/kotlin/snastro/parlanti/adattatori/persistenza/)' | grep -q .`
- **repo-parlanti** (consumed/implemented) — owner `porte-parlanti`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `ParlanteRepository`: interface { trova(id: ParlanteId): Parlante?; delProgetto(id: ProgettoId): List<Parlante>; nomeAttivoInUso(progettoId, nome: Nome, escluso: ParlanteId?): Boolean; salva(p: Parlante): Esito<Unit> /* Errore(NomeGiaInUso) from the index */; rimuovi(id: ParlanteId) /* ONLY for INV-25 occasionale cessation */; impronteDiRegistrazione(id: RegistrazioneId): List<RigaImpronta>; impronteDelProgetto(id: ProgettoId): List<RigaImpronta>; aggiornaImpronta(attesa: RigaImpronta, impronta: Impronta, sorgente: String, modello: String): Boolean /* compare-and-set UPDATE of the ONE row (attesa.parlanteId, attesa.voceRef) only if it still exists with sorgente == attesa.sorgente AND modello == attesa.modello; true iff 1 row updated; NEVER inserts (ADR 0009/0012 Amendment (b): no resurrection) */ }
    - `RigaImpronta`: data class(parlanteId: ParlanteId, voceRef: VoceRef, sorgente: String, modello: String) in parlanti:applicazione.porte — print row metadata, never the embedding
    - `AttribuzioneRepository`: interface { trova(v: VoceRef): Attribuzione?; diRegistrazione(id: RegistrazioneId): List<Attribuzione>; diParlante(id: ParlanteId): List<Attribuzione>; salva(a: Attribuzione); rimuovi(v: VoceRef) }
- **voci-per-parlanti** (consumed/implemented) — owner `porta-lettore-voci`, supplier `api-trascritto`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `LettoreVoci`: interface { fun voci(id: RegistrazioneId): List<VoceVista>? } — null iff no Trascritto (INV-5); Voci ordered by voceId
    - `VoceVista`: data class(voceRef: VoceRef, intervalli: List<IntervalloMs>) — intervals of the Voce's current Segmenti, ordered by inizioMs (tie: segmentoId); NEVER text
  - keys (minting rules):
    - `VoceRef`: composite (registrazioneId, voceId), typed kernel VO because >=2 contexts use it — correlation key of Attribuzione, ImprontaVocale and the Documento name map; stable as its parts
    - `VoceId`: minted by the trascritto aggregate from its persisted counter prossimaVoce — at creation 1..n in order of FIRST APPEARANCE (smallest turn inizioMs, tie: diarizer voceIndice); DividiVoce / riassegna-to-new take prossimaVoce++; never reused, never renumbered, == the n of the label 'Voce n'; stable for the life of one Trascritto GENERATION: a Ritrascrivi replacement (ADR 0018) is a fresh Trascritto.crea numbered from 1 again, and every VoceRef-keyed Parlanti row of the old generation is purged in the same transaction (TrascrittoSostituito)
- **eventi-revisione** (consumed/implemented) — owner `eventi-pubblicati`, supplier `revisione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `VociUnite`: data class(registrazioneId: RegistrazioneId, sopravvissuta: VoceId, rimossa: VoceId) : EventoPubblicato
    - `VoceDivisa`: data class(registrazioneId: RegistrazioneId, origine: VoceId, nuova: VoceId, segmentiSpostati: List<SegmentoId>) : EventoPubblicato
    - `SegmentoRiassegnato`: data class(registrazioneId: RegistrazioneId, segmentoId: SegmentoId, da: VoceId, a: VoceId, daRimossa: Boolean, aNuova: Boolean) : EventoPubblicato
  - keys (minting rules):
    - `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; also names audio/<id>.<ext>, cache/audio/<id>.wav and every EstrattoRef
    - `VoceId`: minted by the trascritto aggregate from its persisted counter prossimaVoce — at creation 1..n in order of FIRST APPEARANCE (smallest turn inizioMs, tie: diarizer voceIndice); DividiVoce / riassegna-to-new take prossimaVoce++; never reused, never renumbered, == the n of the label 'Voce n'; stable for the life of one Trascritto GENERATION: a Ritrascrivi replacement (ADR 0018) is a fresh Trascritto.crea numbered from 1 again, and every VoceRef-keyed Parlanti row of the old generation is purged in the same transaction (TrascrittoSostituito)
    - `SegmentoId`: minted by the trascritto aggregate at creation only, 1..m in order (inizioMs, then voceId); no Segmento is ever created afterwards (INV-8) — stable for the Trascritto generation's life (ADR 0018: a replacement renumbers from 1)
  - delivery: Parlanti revisione-policy → in-process, SYNCHRONOUS inside the publishing command's UnitaDiLavoro transaction, in emission order, exactly once per commit attempt; an Esito.Errore or exception from a sync subscriber rolls the whole command back (ADR 0012). Documento / UI refresh / Parlanti RiallineaImpronte (abbonato-riallineamento-impronte) → in-process, AFTER COMMIT only (never on rollback), at-least-once, on a background coroutine, coalesced per registrazioneId; subscribers must be idempotent (INV-23); single writer per key (one process, one DB) so no cross-stream reordering hazard

Sources: ADRs 0002, 0003, 0006, 0007, 0009, 0012 (.mismagent/decisions/); features/trascrizione-con-parlanti/tactical-model.md § Parlanti (INV-21, INV-25, Q-2, Q-3), ADR 0012.
