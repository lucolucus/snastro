---
id: "parlante"
type: "aggregate"
context: "parlanti"
side: "app"
wave: 2
module: ":parlanti:dominio"
consumes:
  - "kernel-pl"
depends_on: []
related_adrs:
  - "0002"
  - "0003"
  - "0007"
  - "0009"
  - "0012"
invariants:
  - "INV-13 StatoParlante = eliminato ⇒ zero ImprontaVocale; eliminato is terminal: no rinomina, no promozione, no new Attribuzione (sole, policy-only exception: the INV-21 unire re-keying of its tombstone Attribuzione — attribuzione/revisione-policy; no print is ever created); Nome and past Attribuzioni kept"
  - "INV-14 a Parlante holds at most one ImprontaVocale per VoceRef; prints are kept individually (adding one never replaces or averages the others)"
  - "INV-18 promozione only occasionale → ricorrente; ImprontaVocale unchanged; only TipoParlante and optionally Nome change"
invariant_fields:
  - "stato"
  - "nome / nome_normalizzato"
  - "tipo"
  - "impronte"
identity: "ParlanteId — UUID from GeneratoreId"
tables:
  - "parlante"
  - "impronta_vocale"
owns_boundaries:
  agg-parlante:
    projection: "in-process"
    contract_test: "invariant-test"
    pinned_types:
      "Parlante.crea": "(id, progettoId, nome: Nome, tipo: TipoParlante): Creato<Parlante, ParlanteCreato>"
      "Parlante.rinomina": "(nome: Nome): Esito<ParlanteRinominato>"
      "Parlante.promuovi": "(nome: Nome?): Esito<ParlantePromosso>"
      "Parlante.elimina": "(): Esito<ParlanteEliminato> — purges every ImprontaVocale in the same call"
      "Parlante.registraImpronta": "(voceRef: VoceRef, impronta: Impronta, sorgente: String /* SorgenteImpronta.chiave */, modello: String /* EstrattoreImpronta.modello */): Esito<Unit> — insert or replace the ONE print of that VoceRef, never touches others"
      "Parlante.trasferisciImpronta": "(da: VoceRef, a: VoceRef): Unit — policy-only (INV-21 unire inheritance/re-keying): moves the print of `da` to key `a` keeping impronta, sorgente and modello (so it is stale by construction, refreshed after commit by RiallineaImpronte); no-op when there is no print for `da` (always so for an eliminato); require no print already present for `a`"
      ImprontaVocale: "entity data class(voceRef: VoceRef, impronta: Impronta, sorgente: String, modello: String) in parlanti:dominio, exposed read-only via Parlante.impronte: List<ImprontaVocale>; fun obsoleta(chiaveCorrente: String, modelloCorrente: String): Boolean = sorgente != chiaveCorrente || modello != modelloCorrente (the ONE staleness rule, ADR 0012 (b)); same rule as companion ImprontaVocale.obsoleta(sorgente, modello, chiaveCorrente, modelloCorrente) for callers holding only row metadata"
      "Parlante.rimuoviImpronta": "(voceRef: VoceRef): Unit"
      Nome: "smart-constructor VO: Nome.di(String): Esito<Nome>; normalizzato = trim().lowercase(Locale.ROOT)"
      Impronta: "class(valori: FloatArray) in parlanti:dominio, explicit equals/hashCode"
      "named predicates": "attivo, eliminato, occasionale, haImpronte"
---
# parlante — Aggregato Parlante (+ ImprontaVocale)

## What to do
Parlante root (dev-architecture #aggregato) with Nome VO (normalizzato), TipoParlante, StatoParlante, ImprontaVocale entities (voceRef, Impronta, sorgente, modello — Impronta lives in parlanti:dominio) with the staleness predicate obsoleta; registraImpronta(voceRef, impronta, sorgente, modello) and the policy-only trasferisciImpronta(da, a). Built BEFORE attribuzione (shared ErroriParlanti.kt, R20).

Note: AMENDED 2026-09-23 (ADR 0012 / 0009 Amendment (b)): ImprontaVocale carries sorgente (SorgenteImpronta.chiave) + modello (EstrattoreImpronta.modello); registraImpronta gains them; new trasferisciImpronta and the obsoleta predicate (pinned in agg-parlante); INV-13 text names the policy-only unire exception. FOLLOW-UP REQUIRED: merged before ADR 0012 Amendment (b); the merged code does not yet satisfy the amended criteria above — a rework/fix block must land them.

### Invariants owned here (one test each, name starts with the tag)
- INV-13 StatoParlante = eliminato ⇒ zero ImprontaVocale; eliminato is terminal: no rinomina, no promozione, no new Attribuzione (sole, policy-only exception: the INV-21 unire re-keying of its tombstone Attribuzione — attribuzione/revisione-policy; no print is ever created); Nome and past Attribuzioni kept
- INV-14 a Parlante holds at most one ImprontaVocale per VoceRef; prints are kept individually (adding one never replaces or averages the others)
- INV-18 promozione only occasionale → ricorrente; ImprontaVocale unchanged; only TipoParlante and optionally Nome change

## Tasks
- INV-13 elimina rimuove tutte le impronte e rende il Parlante eliminato; su un eliminato rinomina, promuovi, registraImpronta ed elimina restituiscono Errore(ParlanteEliminatoNonModificabile)
- INV-14 registraImpronta con un VoceRef già presente sostituisce solo quella impronta; con un VoceRef nuovo la aggiunge senza toccare le altre
- INV-18 promuovi su un ricorrente → Errore(PromozioneNonAmmessa); su un occasionale cambia solo tipo (e nome se dato) e lascia le impronte identiche
- AC-22 Nome.di rifiuta testo vuoto; normalizzato = trim + minuscole Locale.ROOT ('  Marco ' e 'marco' hanno lo stesso normalizzato)
- INV-13 su un eliminato trasferisciImpronta non crea impronte: resta a zero impronte
- INV-14 trasferisciImpronta(da, a) ri-chiava l'impronta di da su a con la stessa Impronta, sorgente e modello, senza toccare le altre; senza impronta per da non fa nulla
- AC-268 registraImpronta conserva sorgente e modello; ImprontaVocale.obsoleta(chiaveCorrente, modelloCorrente) è vera se la sorgente differisce dalla chiave corrente o il modello differisce, falsa se coincidono entrambi

## Dependencies
- **agg-parlante** (OWNED here — built before its consumers) — owner `parlante`, projection in-process, contract_test **invariant-test**
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

Sources: ADRs 0002, 0003, 0007, 0009, 0012 (.mismagent/decisions/); features/trascrizione-con-parlanti/tactical-model.md § Parlanti.
