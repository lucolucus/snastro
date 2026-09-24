---
id: "sostituzione-trascritto-policy"
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
depends_on: []
related_adrs:
  - "0002"
  - "0003"
  - "0006"
  - "0007"
  - "0009"
  - "0012"
  - "0018"
commands:
  - "ApplicaSostituzioneTrascritto"
invariants:
  - "INV-15 an ImprontaVocale from VoceRef v on Parlante P exists iff a confirmed Attribuzione(v) = P exists and P is attivo — after a replacement NO row keyed by an old VoceRef of the Registrazione survives (defensively also a print row with no Attribuzione)"
  - "INV-25 a Parlante left without Attribuzioni: occasionale ceases to exist; ricorrente is kept"
---
# sostituzione-trascritto-policy — Policy di sostituzione del Trascritto (purga dei dati Parlanti della vecchia generazione)

## What to do
ApplicaSostituzioneTrascrittoPolitica.applica(registrazioneId): when a Trascritto is replaced (TrascrittoSostituito, inside the completion transaction) purge every Attribuzione and every print row keyed by a VoceRef of that Registrazione, for every Parlante, then apply INV-25 (occasionale left without Attribuzioni ceases, ricorrente stays). Structural only, through the aggregates.

Note: NEW 2026-09-24 (ADR 0018 §3, INV-25 confirmed by the user in Amendment 2026-09-24 (b) §1): ApplicaSostituzioneTrascrittoPolitica(parlanti: ParlanteRepository, attribuzioni: AttribuzioneRepository).applica(registrazioneId: RegistrazioneId): Esito<Unit>. Structural only (ADR 0012 (b) enforced_by covers ..politiche: no EstrattoreImpronta/DecodificatoreAudio/CampioniAudio/Impronta), goes through the aggregates (RC-1: AttribuzioneRepository.diRegistrazione + rimuovi, Parlante.rimuoviImpronta + ParlanteRepository.salva, ParlanteRepository.rimuovi for INV-25), no new port. Invoked by abbonato-revisione-parlanti on TrascrittoSostituito, inside the completion transaction. No mapping old→new Voci is ever attempted.

### Invariants owned here (one test each, name starts with the tag)
- INV-15 an ImprontaVocale from VoceRef v on Parlante P exists iff a confirmed Attribuzione(v) = P exists and P is attivo — after a replacement NO row keyed by an old VoceRef of the Registrazione survives (defensively also a print row with no Attribuzione)
- INV-25 a Parlante left without Attribuzioni: occasionale ceases to exist; ricorrente is kept

## Tasks
- AC-428 (INV-15) After applica(r) there are zero Attribuzioni and zero print rows keyed by any VoceRef of r, for every Parlante, attivo or eliminato, including a print row of r with no Attribuzione (defensive, via impronteDiRegistrazione); Attribuzioni and prints of any other Registrazione are untouched (fakes: 2 Registrazioni, 3 Parlanti; rows of the other Registrazione identical before and after)
- AC-429 (INV-25) after the purge: an attivo occasionale whose only Attribuzioni were in r no longer exists (rimuovi); an attivo occasionale that also has an Attribuzione in another Registrazione is kept, with that print; a ricorrente is kept with the same Nome, tipo and stato and its prints of other Registrazioni, even with zero Attribuzioni and zero prints; an eliminato tombstone is kept (still eliminato, same Nome) and its Attribuzione in r is gone
- AC-430 Idempotent and cheap: applica twice gives the same state as once; on a Registrazione with no Attribuzione and no print it returns Ok and does no salva/rimuovi (counting fakes); EstrattoreImprontaFinta and DecodificatoreAudioFinta are never invoked
- AC-431 An Esito.Errore from ParlanteRepository.salva is returned unchanged; nothing is swallowed, so the caller's transaction rolls back

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

Sources: ADRs 0002, 0003, 0006, 0007, 0009, 0012, 0018 (.mismagent/decisions/); ADR 0018 §3 (+ Amendment 2026-09-24 (b) §1), features/trascrizione-con-parlanti/tactical-model.md § Parlanti Policy (Amendment 2026-09-24 ADR 0018), ADR 0009/0012.
