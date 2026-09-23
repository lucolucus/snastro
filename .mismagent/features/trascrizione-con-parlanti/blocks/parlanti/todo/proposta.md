---
id: "proposta"
type: "read-model"
context: "parlanti"
side: "app"
wave: 5
module: ":parlanti:applicazione (..letture)"
consumes:
  - "kernel-pl"
  - "agg-parlante"
  - "repo-parlanti"
  - "registrazione-per-parlanti"
  - "voci-per-parlanti"
  - "tec-decodifica-parlanti"
  - "tec-estrattore-impronta"
  - "tec-confronto-impronte"
  - "sorgente-impronta-pl"
  - "eventi-parlanti"
depends_on:
  - "estratto-audio"
related_adrs:
  - "0002"
  - "0003"
  - "0004"
  - "0005"
  - "0006"
  - "0007"
  - "0009"
  - "0012"
view_shape:
  proposta: "{voceId, candidati: List<{parlanteId, nome, tipoParlante, fascia: Fascia, estratto: EstrattoRef}>}"
view_sources:
  proposta: "parlanteId, nome, tipoParlante ← Parlante (attivi con impronte, same progettoId via registrazione-per-parlanti); fascia ← ConfrontoImpronte(transient Impronta of the Voce from EstrattoreImpronta over DecodificatoreAudio.campioni(SorgenteImpronta.di(voci-per-parlanti intervals).intervalli), against only the Parlante's prints whose modello == EstrattoreImpronta.modello); estratto ← estratto-audio(the best print's source VoceRef)"
invariants:
  - "INV-20 Candidati are only attivo Parlanti of the same Progetto with >= 1 ImprontaVocale; ranked ricorrente first then by Fascia (forte > debole > nessuna); every Candidato has an EstrattoAudio; no numeric score leaves the read-model"
---
# proposta — Proposta per Voce

## What to do
On-demand Proposta per not-yet-attributed Voce; transient embedding extracted from SorgenteImpronta.di(intervalli) outside any transaction and kept in memory only; compared only against prints whose modello equals EstrattoreImpronta.modello; cached for the open Registrazione and invalidated on revisione/attribution events and ImpronteRiallineate. Ties (same TipoParlante and Fascia) by Nome alphabetical.

### Invariants owned here (one test each, name starts with the tag)
- INV-20 Candidati are only attivo Parlanti of the same Progetto with >= 1 ImprontaVocale; ranked ricorrente first then by Fascia (forte > debole > nessuna); every Candidato has an EstrattoAudio; no numeric score leaves the read-model

### view_shape (field ← source)
- `proposta`: {voceId, candidati: List<{parlanteId, nome, tipoParlante, fascia: Fascia, estratto: EstrattoRef}>} ← parlanteId, nome, tipoParlante ← Parlante (attivi con impronte, same progettoId via registrazione-per-parlanti); fascia ← ConfrontoImpronte(transient Impronta of the Voce from EstrattoreImpronta over DecodificatoreAudio.campioni(SorgenteImpronta.di(voci-per-parlanti intervals).intervalli), against only the Parlante's prints whose modello == EstrattoreImpronta.modello); estratto ← estratto-audio(the best print's source VoceRef)

## Tasks
- INV-20 solo Parlanti attivi dello stesso Progetto con almeno un'impronta sono Candidati
- INV-20 ordine: prima i ricorrenti, poi per Fascia; a parità di tipo e Fascia, per Nome in ordine alfabetico
- AC-170 La Fascia di un Candidato è la migliore tra le sue impronte
- INV-20 la vista espone voceId e per ogni Candidato parlanteId, nome, tipoParlante, fascia ed estratto — nessun numero (by-construction sul tipo; il test verifica la forma)
- AC-171 Galleria vuota → lista di Candidati vuota
- AC-172 Calcolare una Proposta non scrive nessuna riga (conteggio impronta_vocale invariato)
- AC-173 Dopo una Revisione, un'Attribuzione o ImpronteRiallineate(registrazioneId) la Proposta è ricalcolata (cache invalidata)
- AC-308 L'impronta transitoria della Voce è estratta da SorgenteImpronta.di(intervalli della Voce): gli intervalli decodificati sono esattamente SorgenteImpronta.intervalli, e nessuna transazione è aperta durante decodifica ed estrazione
- AC-309 Le impronte con modello diverso da EstrattoreImpronta.modello sono ignorate nel confronto: un Parlante che ha solo impronte di un altro modello non è Candidato finché non sono riallineate

## Dependencies
- Blocks built first: `estratto-audio` (wave 4)
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
- **registrazione-per-parlanti** (consumed/implemented) — owner `porta-registrazione-parlanti`, supplier `catalogo-registrazioni`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `LettoreRegistrazione`: interface { fun registrazione(id: RegistrazioneId): RegistrazioneVista? } — Parlanti's own copy
    - `RegistrazioneVista`: data class(registrazioneId: RegistrazioneId, progettoId: ProgettoId, titolo: String, riferimentoAudio: RiferimentoAudio, dataRegistrazione: LocalDate, durataMs: Long) — progettoId scopes INV-17, dataRegistrazione feeds 'Ospite del dd/MM/yyyy' (INV-19)
  - keys (minting rules):
    - `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; also names audio/<id>.<ext>, cache/audio/<id>.wav and every EstrattoRef
    - `ProgettoId`: minted by crea-progetto via kernel GeneratoreId (UUID v4 string) — immutable; stored in progetto.db so it survives moving/copying the project folder
    - `RiferimentoAudio`: minted by audio-progetto (ArchivioAudio.copia): 'audio/<registrazioneId>.<source extension lowercased>', relative to the project folder — immutable
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
- **tec-confronto-impronte** (consumed/implemented) — owner `porte-parlanti`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `ConfrontoImpronte`: interface { fun fascia(voce: Impronta, impronte: List<Impronta>): Fascia } — the BEST band over the Parlante's prints; empty list → NESSUNA
    - `Fascia`: enum FORTE | DEBOLE | NESSUNA (parlanti:applicazione) — never a number outside the adapter
    - `SoglieFascia`: data class(forte: Double, debole: Double) — require forte > debole; values from spike impronta-vocale-affidabilita, injected as config
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
    - `ImpronteRiallineate`: data class(registrazioneId: RegistrazioneId) : EventoPubblicato — published by riallinea-impronte after the commit of >= 1 refreshed print row (ADR 0012 Amendment (b)); consumers: proposta (cache invalidation), avvio-composizione (AggiornamentiVista); NOT Documento (prints do not change it)
    - `TipoParlanteVista`: enum RICORRENTE | OCCASIONALE (parlanti:applicazione)
  - keys (minting rules):
    - `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; also names audio/<id>.<ext>, cache/audio/<id>.wav and every EstrattoRef
    - `VoceRef`: composite (registrazioneId, voceId), typed kernel VO because >=2 contexts use it — correlation key of Attribuzione, ImprontaVocale and the Documento name map; stable as its parts
    - `ParlanteId`: minted by conferma-attribuzione (new Nome) and salta-voce via GeneratoreId (UUID v4) — stable across rinomina, promozione and eliminazione (tombstone keeps it); disappears only via INV-25 (occasionale left without Attribuzioni)
    - `ProgettoId`: minted by crea-progetto via kernel GeneratoreId (UUID v4 string) — immutable; stored in progetto.db so it survives moving/copying the project folder
  - delivery: in-process, AFTER COMMIT only (never on rollback), at-least-once, on a background coroutine, coalesced per registrazioneId; subscribers must be idempotent (INV-23); single writer per key (one process, one DB) so no cross-stream reordering hazard

Sources: ADRs 0002, 0003, 0004, 0005, 0006, 0007, 0009, 0012 (.mismagent/decisions/); features/trascrizione-con-parlanti/tactical-model.md § Parlanti (INV-20, Read-models), features/trascrizione-con-parlanti/architetture/architecture-overview.md (Arbitration), R24.
