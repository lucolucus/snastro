---
id: "piano-riassegnazione"
type: "read-model"
context: "parlanti"
side: "app"
wave: 5
release: "R2"
module: ":parlanti:applicazione (..letture)"
consumes:
  - "kernel-pl"
  - "agg-parlante"
  - "agg-attribuzione"
  - "repo-parlanti"
  - "voci-per-parlanti"
  - "tec-decodifica-parlanti"
  - "tec-estrattore-impronta"
  - "tec-classificatore-somiglianza"
  - "sorgente-impronta-pl"
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
  - "0017"
  - "0019"
model_hint: "deep"
view_shape:
  piano: "{registrazioneId, spostamenti: List<{segmentoId, da, a, intervallo}>, incerte: Int}"
view_sources:
  piano: "≡ pinned type PianoRiassegnazione of piano-per-somiglianza; spostamenti ← LettoreVoci.segmenti (voci-per-parlanti) + AttribuzioneRepository.diRegistrazione + Parlante stato attivo (repo-parlanti) + ClassificatoreSomiglianza over transient Impronte (DecodificatoreAudio + SorgenteImpronta.di + EstrattoreImpronta); incerte ← the movable Segmenti classified Incerta, shorter than 1 000 ms, or dropped by the INV-27 guard"
invariants:
  - "INV-27 a PianoRiassegnazione of Registrazione R moves a Segmento s to Voce T only if: s is not confermato, is >= 1 000 ms and lies on a Voce that is not frozen (unattributed, or attributed to a reference Parlante); s is classified Sicura(P) for a reference Parlante P; T is P's target Voce (the lowest voceId attributed to P in R) and s is not already on T; after the whole plan every reference Parlante still has >= 1 Segmento in R (otherwise every move out of that Parlante's Voci is dropped and counted as incerta, repeated until stable). A reference Parlante is an attivo Parlante attributed to >= 1 Voce of R with >= 1 reference: its confermato Segmenti >= 1 000 ms if it has any ('frasi confermate'), otherwise every Segmento >= 1 000 ms on its Voci ('intera Voce'); an eliminato never is one; a plan needs >= 2. It writes nothing, keeps no embedding after it returns and exposes no similarity number (ADR 0019 Amendment 2026-09-24 (b).1)"
owns_boundaries:
  piano-per-somiglianza:
    projection: "in-process"
    contract_test: "consumer-driven"
    pinned_types:
      PianoRiassegnazioneQuery: "fun calcola(id: RegistrazioneId, progresso: (fatti: Int, totale: Int) -> Unit): Esito<PianoRiassegnazione> — errors TrascrittoNonTrovato, ErroreParlanti.RiferimentiInsufficienti(registrazioneId); throws InterruptedException on cancellation; never opens a transaction; writes nothing"
      PianoRiassegnazione: "data class(registrazioneId: RegistrazioneId, spostamenti: List<SpostamentoProposto>, incerte: Int) — no similarity number, no Impronta: the glue may HOLD it until Applica (ADR 0019 Amendment (b).2)"
      SpostamentoProposto: "data class(segmentoId: SegmentoId, da: VoceId, a: VoceId, intervallo: IntervalloMs) — primitives + kernel VOs only; the glue maps it 1:1 to SpostamentoSegmento; ordered by (intervallo.inizioMs, segmentoId)"
      "ErroreParlanti.RiferimentiInsufficienti": "data class(registrazioneId: RegistrazioneId) : ErroreParlanti (ErroriParlanti.kt) — fewer than 2 reference Parlanti (INV-27)"
---
# piano-riassegnazione — PianoRiassegnazione (il piano di «Riassegna per somiglianza»)

## What to do
PianoRiassegnazioneQuery.calcola(id, progresso): the Parlanti read-model that computes the plan of 'Riassegna per somiglianza' — never stored, never writes. It derives the reference Parlanti (confirmed sentences >= 1 s, otherwise the whole named Voce: ADR 0019 Amendment (b).1), the frozen Voci and the movable Segmenti; embeds references and candidates once each outside any transaction (DecodificatoreAudio + SorgenteImpronta + EstrattoreImpronta); classifies through ClassificatoreSomiglianza; applies the last-Segmento guard; returns spostamenti (to each Parlante's target Voce, the lowest voceId) and the count of incerte.

Note: NEW 2026-09-24 (ADR 0019 §4.1–4.4, §4.7–4.8 + Amendment 2026-09-24 (b).1, manifest delta 2026-09-24-semi-automatica): computed, never stored, never writes; embeds outside any transaction (one estrai per extracted Segmento, one Mutex hold each, ADR 0017 §1.2), classifies through ClassificatoreSomiglianza, throws InterruptedException on cancellation. The plan's intervallo is the Segmento's CURRENT interval (the stale-guard of agg-trascritto riassegnaInBlocco); the plan exposes no similarity (by type) and holds no Impronta, so the glue may HOLD it until Applica (Amendment (b).2). model_hint deep: folds >= 2 boundaries and carries the reference-mode + guard rule.

### Invariants owned here (one test each, name starts with the tag)
- INV-27 a PianoRiassegnazione of Registrazione R moves a Segmento s to Voce T only if: s is not confermato, is >= 1 000 ms and lies on a Voce that is not frozen (unattributed, or attributed to a reference Parlante); s is classified Sicura(P) for a reference Parlante P; T is P's target Voce (the lowest voceId attributed to P in R) and s is not already on T; after the whole plan every reference Parlante still has >= 1 Segmento in R (otherwise every move out of that Parlante's Voci is dropped and counted as incerta, repeated until stable). A reference Parlante is an attivo Parlante attributed to >= 1 Voce of R with >= 1 reference: its confermato Segmenti >= 1 000 ms if it has any ('frasi confermate'), otherwise every Segmento >= 1 000 ms on its Voci ('intera Voce'); an eliminato never is one; a plan needs >= 2. It writes nothing, keeps no embedding after it returns and exposes no similarity number (ADR 0019 Amendment 2026-09-24 (b).1)

### view_shape (field ← source)
- `piano`: {registrazioneId, spostamenti: List<{segmentoId, da, a, intervallo}>, incerte: Int} ← ≡ pinned type PianoRiassegnazione of piano-per-somiglianza; spostamenti ← LettoreVoci.segmenti (voci-per-parlanti) + AttribuzioneRepository.diRegistrazione + Parlante stato attivo (repo-parlanti) + ClassificatoreSomiglianza over transient Impronte (DecodificatoreAudio + SorgenteImpronta.di + EstrattoreImpronta); incerte ← the movable Segmenti classified Incerta, shorter than 1 000 ms, or dropped by the INV-27 guard

## Tasks
- INV-27 table test on fixture Voci / Attribuzioni / Segmenti with a classifier fake: a Segmento moves only if all four conditions hold (not confermato / >= 1 s / not frozen; Sicura(P) for a reference P; not already on P's target Voce; the last-Segmento guard); one row per failing condition, each giving no spostamento
- AC-501 (REWORDED 2026-09-24, Amendment (b).1) Reference modes, table test with a classifier fake that records the riferimenti map it receives: P with a confirmed 2 s Segmento and 5 unconfirmed >= 1 s Segmenti → references = the confirmed one only ('frasi confermate'), the 5 are movable; P with no confirmed Segmento whose Voce holds 3 s, 2 s and 0.6 s Segmenti → references = the 3 s and 2 s ones ('intera Voce'), the 0.6 s one is an incerta; P whose only confirmed Segmento is 0.8 s → the fallback applies; P on Voci 2 and 4, neither confirmed → the >= 1 s Segmenti of BOTH Voci; an eliminato Parlante has none even with a confirmed Segmento on its Voce; a confirmed Segmento on an unattributed Voce is no one's reference
- AC-502 (REWORDED) Fewer than 2 reference Parlanti, in either mode → Errore(RiferimentiInsufficienti) with 0 calls to DecodificatoreAudio and EstrattoreImpronta (counting fakes): one named attivo Parlante → error; two named attivo Parlanti with no confirmed sentence, each with a >= 1 s Segmento → NO error (fallback)
- AC-503 (REWORDED) Frozen Voci are exactly the Voci attributed to an eliminato, or to an attivo Parlante with no Segmento >= 1 000 ms on any of its Voci: their Segmenti are never extracted, never appear in spostamenti as da and are never a target a; a named Voce whose Parlante has no confirmed sentence is NOT frozen — its non-confermato >= 1 s Segmenti are candidates (fixture: one of them Sicura(Q) → a spostamento to Q's target); confirmed Segmenti never appear in spostamenti
- AC-504 Movable Segmenti shorter than 1 000 ms are not extracted; each counts once in incerte
- AC-505 Target Voce = the lowest voceId among the Voci attributed to the chosen Parlante (fixture: P on Voci 4 and 2 → target 2); a Sicura Segmento already on the target → no spostamento; one on another Voce, including P's other Voce 4 → spostamento(da, a = 2, intervallo = the Segmento's)
- AC-506 Incerta → no spostamento, counted in incerte
- AC-507 (REWORDED) Exactly one estrai per extracted Segmento — the union of references and movable Segmenti >= 1 s; a Segmento that is both an 'intera Voce' reference and a candidate is extracted ONCE and its embedding serves both roles (counting fake keyed by interval); each over DecodificatoreAudio.campioni(id, SorgenteImpronta.di(listOf(intervallo)).intervalli); no transaction is open (the fakes throw if one is); progresso(fatti, totale) is called once after each estrai with a constant totale = number of extractions and fatti = 1..totale
- AC-508 An InterruptedException from estrai (at the 3rd call) propagates: no result, nothing written, and the next calcola extracts again from the start
- AC-509 calcola writes nothing (attribuzione / impronta_vocale / parlante row counts unchanged); two consecutive calcola make 2x the estrai calls, so no embedding is retained (ADR 0009; code review: no field or cache holds an Impronta)
- AC-510 (REWORDED) Idempotence when EVERY reference Parlante is in the 'frasi confermate' mode: apply the plan to the LettoreVoci fake, keep the same deterministic extractor fake, calcola again → spostamenti empty and incerte unchanged; with a fallback Parlante idempotence is NOT claimed (AC-543)
- AC-543 (NEW, Amendment (b).1) 'Intera Voce' references are derived from the CURRENT state at every calcola, with no memory of earlier runs: fixture — Q in fallback mode on Voce 5; run 1 plans s (Voce 3) onto Q's target Voce 5 and t (Voce 5) away to P; apply to the fake and calcola again → the classifier fake records Q's references include s and exclude t; no confermato Segmento is moved in either run
- AC-544 (NEW, Amendment (b).1) Last-Segmento guard: Q in fallback mode with one Voce of 3 Segmenti >= 1 s, all Sicura(P) → NO spostamento out of Q's Voce and those 3 count in incerte; the same with Q owning a second Voce that keeps an incerta → the 3 moves ARE planned; cascade fixture (dropping Q's moves would empty R's Voce) → R's moves are dropped too; the result is deterministic (same plan twice)

## Dependencies
- **piano-per-somiglianza** (OWNED here — built before its consumers) — owner `piano-riassegnazione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `PianoRiassegnazioneQuery`: fun calcola(id: RegistrazioneId, progresso: (fatti: Int, totale: Int) -> Unit): Esito<PianoRiassegnazione> — errors TrascrittoNonTrovato, ErroreParlanti.RiferimentiInsufficienti(registrazioneId); throws InterruptedException on cancellation; never opens a transaction; writes nothing
    - `PianoRiassegnazione`: data class(registrazioneId: RegistrazioneId, spostamenti: List<SpostamentoProposto>, incerte: Int) — no similarity number, no Impronta: the glue may HOLD it until Applica (ADR 0019 Amendment (b).2)
    - `SpostamentoProposto`: data class(segmentoId: SegmentoId, da: VoceId, a: VoceId, intervallo: IntervalloMs) — primitives + kernel VOs only; the glue maps it 1:1 to SpostamentoSegmento; ordered by (intervallo.inizioMs, segmentoId)
    - `ErroreParlanti.RiferimentiInsufficienti`: data class(registrazioneId: RegistrazioneId) : ErroreParlanti (ErroriParlanti.kt) — fewer than 2 reference Parlanti (INV-27)
  - keys (minting rules):
    - `VoceId`: minted by the trascritto aggregate from its persisted counter prossimaVoce — at creation 1..n in order of FIRST APPEARANCE (smallest turn inizioMs, tie: diarizer voceIndice); DividiVoce / riassegna-to-new take prossimaVoce++; never reused, never renumbered, == the n of the label 'Voce n'; stable for the life of one Trascritto GENERATION: a Ritrascrivi replacement (ADR 0018) is a fresh Trascritto.crea numbered from 1 again, and every VoceRef-keyed Parlanti row of the old generation is purged in the same transaction (TrascrittoSostituito)
    - `SegmentoId`: minted by the trascritto aggregate at creation only, 1..m in order (inizioMs, then voceId); no Segmento is ever created afterwards (INV-8) — stable for the Trascritto generation's life (ADR 0018: a replacement renumbers from 1)
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
    - `LettoreVoci`: interface { fun voci(id: RegistrazioneId): List<VoceVista>?; fun segmenti(id: RegistrazioneId): List<SegmentoDiVoce>? } — null iff no Trascritto (INV-5); Voci ordered by voceId; segmenti = every current Segmento once, ordered by (inizioMs, segmentoId), NEVER text (ADR 0019 §4.1)
    - `SegmentoDiVoce`: data class(segmentoId: SegmentoId, voceId: VoceId, intervallo: IntervalloMs, confermato: Boolean) — NEVER text; supplier side: api-trascritto segmentiDiVoce(id) with its own SegmentoDiVoceVista, mapped 1:1
    - `VoceVista`: data class(voceRef: VoceRef, intervalli: List<IntervalloMs>) — intervals of the Voce's current Segmenti, ordered by inizioMs (tie: segmentoId); NEVER text
  - keys (minting rules):
    - `VoceRef`: composite (registrazioneId, voceId), typed kernel VO because >=2 contexts use it — correlation key of Attribuzione, ImprontaVocale and the Documento name map; stable as its parts
    - `VoceId`: minted by the trascritto aggregate from its persisted counter prossimaVoce — at creation 1..n in order of FIRST APPEARANCE (smallest turn inizioMs, tie: diarizer voceIndice); DividiVoce / riassegna-to-new take prossimaVoce++; never reused, never renumbered, == the n of the label 'Voce n'; stable for the life of one Trascritto GENERATION: a Ritrascrivi replacement (ADR 0018) is a fresh Trascritto.crea numbered from 1 again, and every VoceRef-keyed Parlanti row of the old generation is purged in the same transaction (TrascrittoSostituito)
    - `SegmentoId`: minted by the trascritto aggregate at creation only, 1..m in order (inizioMs, then voceId); no Segmento is ever created afterwards (INV-8) — stable for the Trascritto generation's life (ADR 0018: a replacement renumbers from 1)
- **tec-decodifica-parlanti** (consumed/implemented) — owner `porte-parlanti`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `DecodificatoreAudio`: interface { fun campioni(id: RegistrazioneId, intervalli: List<IntervalloMs>): CampioniAudio } — concatenation in the given order (Parlanti's own copy); NEVER called while a UnitaDiLavoro transaction is open (ADR 0012 Amendment (b))
    - `DecodificatoreAudioFinta`: testFixtures — takes the UnitaDiLavoroFinta (optional ctor param) and throws IllegalStateException when campioni is invoked while transazioneAperta
- **tec-estrattore-impronta** (consumed/implemented) — owner `porte-parlanti`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `EstrattoreImpronta`: interface { val modello: String /* catalogue id of the embedding model (ADR 0008), stored as impronta_vocale.modello_impronta */; fun estrai(c: CampioniAudio): Impronta } — NEVER called while a UnitaDiLavoro transaction is open; the adapter serializes native use with the pipeline by taking the native Mutex INSIDE estrai (ADR 0012 Amendment (b) points 2, 5); ONE conSessione per estrai call, for ONE print, never kept after return; an interrupt (cancellation) while waiting for the Mutex or during the native extraction → InterruptedException after the session closes, and no Impronta (ADR 0017 §1.2, §1.5)
    - `EstrattoreImprontaFinta`: testFixtures — takes the UnitaDiLavoroFinta (optional ctor param) and throws IllegalStateException when estrai is invoked while transazioneAperta; modello configurable (default "finto")
    - `Impronta`: see agg-parlante (parlanti:dominio)
- **tec-classificatore-somiglianza** (consumed/implemented) — owner `porte-parlanti`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `ClassificatoreSomiglianza`: interface { fun classifica(riferimenti: Map<ParlanteId, List<Impronta>>, frasi: List<Impronta>): List<Classificazione> } — output has the size and order of frasi; riferimenti has >= 2 keys, each non-empty (the caller guarantees it: require); never throws on print data (ADR 0019 §4.3)
    - `Classificazione`: sealed interface (parlanti:applicazione) { data class Sicura(val parlanteId: ParlanteId); data object Incerta } — no number
    - `SoglieSomiglianza`: data class(minima: Double, margine: Double) — require(margine > 0 && minima in -1.0..1.0), NaN rejected; injected as config; PROVISIONAL (ADR 0019 §4.3)
    - `ClassificatoreSomiglianzaFinta`: testFixtures — configurable table frase index → Classificazione; records the riferimenti map it receives
    - `Impronta`: see agg-parlante (parlanti:dominio)
  - keys (minting rules):
    - `ParlanteId`: minted by conferma-attribuzione (new Nome) and salta-voce via GeneratoreId (UUID v4) — stable across rinomina, promozione and eliminazione (tombstone keeps it); disappears only via INV-25 (occasionale left without Attribuzioni)
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

Sources: ADRs 0002, 0003, 0004, 0005, 0006, 0007, 0009, 0012, 0017, 0019 (.mismagent/decisions/); ADR 0009/0012/0017, ADR 0019 §4 + Amendment 2026-09-24 (b).1, features/trascrizione-con-parlanti/tactical-model.md § Amendment 2026-09-24 (ADR 0019).
