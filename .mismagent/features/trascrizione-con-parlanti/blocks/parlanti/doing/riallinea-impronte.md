---
id: "riallinea-impronte"
type: "application-service"
context: "parlanti"
side: "app"
wave: 4
release: "R2"
module: ":parlanti:applicazione (..comandi)"
consumes:
  - "kernel-pl"
  - "agg-parlante"
  - "repo-parlanti"
  - "voci-per-parlanti"
  - "tec-decodifica-parlanti"
  - "tec-estrattore-impronta"
  - "sorgente-impronta-pl"
  - "eventi-parlanti"
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
model_hint: "deep"
commands:
  - "RiallineaImpronte"
  - "RiallineaTutteLeImpronte"
invariants:
  - "INV-15 (freshness half, ADR 0012 Amendment (b)): a row is stale iff sorgente_impronta != SorgenteImpronta.di(current intervals of its Voce).chiave OR modello_impronta != EstrattoreImpronta.modello; only stale rows are re-derived, by compare-and-set UPDATE — existence is never changed here (never INSERT, so a purged print is never resurrected)"
---
# riallinea-impronte — RiallineaImpronte / RiallineaTutteLeImpronte — freschezza delle impronte dopo il commit

## What to do
RiallineaImpronte(registrazioneId): list the Registrazione's print rows, keep the stale ones (ImprontaVocale.obsoleta vs SorgenteImpronta.di(current intervals).chiave and EstrattoreImpronta.modello), decode + extract each OUTSIDE any transaction, then per Voce one short transaction that re-reads the Voce and calls the compare-and-set aggiornaImpronta (never INSERT); publish ImpronteRiallineate when >= 1 row changed. RiallineaTutteLeImpronte(progettoId) runs it for every Registrazione of the Progetto with prints.

Note: ADR 0012 Amendment (b) point 3: run after commit by abbonato-riallineamento-impronte (coalesced per registrazioneId, retried) and at project open by avvio-composizione (RiallineaTutteLeImpronte, background, after RecuperaElaborazioniInterrotte). Staleness decided with the domain rule ImprontaVocale.obsoleta (never re-coded). An extracted Impronta that is not written is simply dropped (ADR 0009 Amendment (b)). Rows per Voce: at most one per Voce in practice (one Attribuzione per Voce), grouped per Voce anyway.

### Invariants owned here (one test each, name starts with the tag)
- INV-15 (freshness half, ADR 0012 Amendment (b)): a row is stale iff sorgente_impronta != SorgenteImpronta.di(current intervals of its Voce).chiave OR modello_impronta != EstrattoreImpronta.modello; only stale rows are re-derived, by compare-and-set UPDATE — existence is never changed here (never INSERT, so a purged print is never resurrected)

## Tasks
- AC-292 RiallineaImpronte(registrazioneId) tocca solo le righe obsolete: con tre righe — fresca, sorgente cambiata, modello diverso — solo le ultime due sono decodificate, estratte e aggiornate; quella fresca non è nemmeno decodificata
- AC-293 La scrittura è un aggiornamento compare-and-set (aggiornaImpronta) in una transazione breve per Voce che rilegge la Voce; non avviene mai un inserimento (conteggio righe invariato)
- AC-294 Una riga cancellata tra l'estrazione e la scrittura (EliminaParlante, Attribuzione cambiata, INV-25) non è resuscitata: nessuna riga creata, esito Ok
- AC-295 Se la Voce cambia di nuovo durante l'estrazione (la sorgente ricalcolata nella transazione differisce da quella estratta) non scrive nulla per quella riga; una seconda esecuzione converge
- AC-296 Idempotente: una seconda esecuzione subito dopo non decodifica, non estrae, non scrive nulla e non pubblica ImpronteRiallineate
- AC-297 Nessuna transazione è aperta durante decodifica ed estrazione (le Finte ML lanciano se transazioneAperta)
- AC-298 Pubblica ImpronteRiallineate(registrazioneId) dopo il commit solo se almeno una riga è stata aggiornata
- AC-299 Una riga la cui Voce non esiste più (o Registrazione senza Trascritto) è saltata senza errore e senza scritture (la rimozione spetta alla revisione-policy)
- AC-300 RiallineaTutteLeImpronte(progettoId) esegue RiallineaImpronte per ogni Registrazione con almeno una riga d'impronta del Progetto (impronteDelProgetto); un fallimento su una Registrazione non impedisce le altre ed è riportato
- AC-301 Un'eccezione di decodifica o estrazione si propaga (ADR 0003, così l'abbonato riprova) dopo che le Voci già riallineate sono state committate; per la Voce fallita nulla è scritto

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
    - `VoceId`: minted by the trascritto aggregate from its persisted counter prossimaVoce — at creation 1..n in order of FIRST APPEARANCE (smallest turn inizioMs, tie: diarizer voceIndice); DividiVoce / riassegna-to-new take prossimaVoce++; never reused, never renumbered, == the n of the label 'Voce n'; stable for the Trascritto's life (= forever: no re-run after completata)
- **tec-decodifica-parlanti** (consumed/implemented) — owner `porte-parlanti`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `DecodificatoreAudio`: interface { fun campioni(id: RegistrazioneId, intervalli: List<IntervalloMs>): CampioniAudio } — concatenation in the given order (Parlanti's own copy); NEVER called while a UnitaDiLavoro transaction is open (ADR 0012 Amendment (b))
    - `DecodificatoreAudioFinta`: testFixtures — takes the UnitaDiLavoroFinta (optional ctor param) and throws IllegalStateException when campioni is invoked while transazioneAperta
- **tec-estrattore-impronta** (consumed/implemented) — owner `porte-parlanti`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `EstrattoreImpronta`: interface { val modello: String /* catalogue id of the embedding model (ADR 0008), stored as impronta_vocale.modello_impronta */; fun estrai(c: CampioniAudio): Impronta } — NEVER called while a UnitaDiLavoro transaction is open; the adapter serializes native use with the pipeline by taking the native Mutex INSIDE estrai (ADR 0012 Amendment (b) points 2, 5)
    - `EstrattoreImprontaFinta`: testFixtures — takes the UnitaDiLavoroFinta (optional ctor param) and throws IllegalStateException when estrai is invoked while transazioneAperta; modello configurable (default "finto")
    - `Impronta`: see agg-parlante (parlanti:dominio)
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
- **eventi-parlanti** (consumed/implemented) — owner `eventi-pubblicati`, supplier `conferma-attribuzione, salta-voce, gestione-parlante, riallinea-impronte`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `AttribuzioneConfermata`: data class(voceRef: VoceRef, parlanteId: ParlanteId, precedente: ParlanteId?) : EventoPubblicato
    - `ParlanteCreato`: data class(parlanteId: ParlanteId, progettoId: ProgettoId, nome: String, tipo: TipoParlanteVista) : EventoPubblicato
    - `ParlanteRinominato`: data class(parlanteId: ParlanteId, nome: String) : EventoPubblicato
    - `ParlantePromosso`: data class(parlanteId: ParlanteId, nome: String, nomeCambiato: Boolean) : EventoPubblicato
    - `ParlanteEliminato`: data class(parlanteId: ParlanteId) : EventoPubblicato — NO Documento change
    - `ImpronteRiallineate`: data class(registrazioneId: RegistrazioneId) : EventoPubblicato — published by riallinea-impronte after the commit of >= 1 refreshed print row (ADR 0012 Amendment (b)); consumers: proposta (cache invalidation), avvio-parlanti (AggiornamentiVista); NOT Documento (prints do not change it)
    - `TipoParlanteVista`: enum RICORRENTE | OCCASIONALE (parlanti:applicazione)
  - keys (minting rules):
    - `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; also names audio/<id>.<ext>, cache/audio/<id>.wav and every EstrattoRef
    - `VoceRef`: composite (registrazioneId, voceId), typed kernel VO because >=2 contexts use it — correlation key of Attribuzione, ImprontaVocale and the Documento name map; stable as its parts
    - `ParlanteId`: minted by conferma-attribuzione (new Nome) and salta-voce via GeneratoreId (UUID v4) — stable across rinomina, promozione and eliminazione (tombstone keeps it); disappears only via INV-25 (occasionale left without Attribuzioni)
    - `ProgettoId`: minted by crea-progetto via kernel GeneratoreId (UUID v4 string) — immutable; stored in progetto.db so it survives moving/copying the project folder
  - delivery: in-process, AFTER COMMIT only (never on rollback), at-least-once, on a background coroutine, coalesced per registrazioneId; subscribers must be idempotent (INV-23); single writer per key (one process, one DB) so no cross-stream reordering hazard

Sources: ADRs 0002, 0003, 0004, 0005, 0006, 0007, 0009, 0012 (.mismagent/decisions/); ADR 0012 Amendment (b) points 2-3, ADR 0009 Amendment (b), features/trascrizione-con-parlanti/tactical-model.md § Parlanti (INV-15, INV-21, Commands RiallineaImpronte).
