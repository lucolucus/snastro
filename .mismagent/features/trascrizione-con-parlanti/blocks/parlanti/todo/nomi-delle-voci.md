---
id: "nomi-delle-voci"
type: "read-model"
context: "parlanti"
side: "app"
wave: 4
module: ":parlanti:applicazione (..letture)"
consumes:
  - "kernel-pl"
  - "agg-parlante"
  - "agg-attribuzione"
  - "repo-parlanti"
  - "nomi-per-documento"
depends_on: []
related_adrs:
  - "0002"
  - "0003"
  - "0006"
  - "0007"
  - "0009"
  - "0012"
view_shape:
  nomi: "Map<VoceRef, String>"
  registrazioniCon: "List<RegistrazioneId>"
view_sources:
  nomi: "≡ nomi-per-documento ← Attribuzione + Parlante.nome (eliminato included)"
  registrazioniCon: "≡ nomi-per-documento ← AttribuzioneRepository.diParlante"
---
# nomi-delle-voci — NomiDelleVoci (API pubblica dei Parlanti)

## What to do
Supplier query API nomi(id) and registrazioniCon(parlanteId) — shapes ARE the pinned type.

### view_shape (field ← source)
- `nomi`: Map<VoceRef, String> ← ≡ nomi-per-documento ← Attribuzione + Parlante.nome (eliminato included)
- `registrazioniCon`: List<RegistrazioneId> ← ≡ nomi-per-documento ← AttribuzioneRepository.diParlante

## Tasks
- AC-101 nomi(id) mappa ogni VoceRef attribuita al Nome del Parlante, anche se eliminato; le Voci non attribuite non compaiono
- AC-102 registrazioniCon(P) restituisce le Registrazioni distinte con almeno un'Attribuzione a P

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
    - `Parlante.registraImpronta`: (voceRef: VoceRef, impronta: Impronta): Esito<Unit> — insert or replace the ONE print of that VoceRef, never touches others
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
  - keys (minting rules):
    - `VoceRef`: composite (registrazioneId, voceId), typed kernel VO because >=2 contexts use it — correlation key of Attribuzione, ImprontaVocale and the Documento name map; stable as its parts
  - §14 gates (must stay green):
    - `! grep -rnE --include='*.kt' --exclude-dir=build '\b(attribuzioneQueries)\b' . | grep -vE '^\./(persistenza/|parlanti/adattatori/src/[A-Za-z]+/kotlin/snastro/parlanti/adattatori/persistenza/)' | grep -q .`
- **repo-parlanti** (consumed/implemented) — owner `porte-parlanti`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `ParlanteRepository`: interface { trova(id: ParlanteId): Parlante?; delProgetto(id: ProgettoId): List<Parlante>; nomeAttivoInUso(progettoId, nome: Nome, escluso: ParlanteId?): Boolean; salva(p: Parlante): Esito<Unit> /* Errore(NomeGiaInUso) from the index */; rimuovi(id: ParlanteId) /* ONLY for INV-25 occasionale cessation */ }
    - `AttribuzioneRepository`: interface { trova(v: VoceRef): Attribuzione?; diRegistrazione(id: RegistrazioneId): List<Attribuzione>; diParlante(id: ParlanteId): List<Attribuzione>; salva(a: Attribuzione); rimuovi(v: VoceRef) }
- **nomi-per-documento** (consumed/implemented) — owner `porta-lettore-nomi`, supplier `nomi-delle-voci`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `LettoreNomi`: interface { fun nomi(id: RegistrazioneId): Map<VoceRef, String>; fun registrazioniCon(p: ParlanteId): List<RegistrazioneId> } — attributed Voci only; an eliminato Parlante still resolves to its Nome (INV-24)
  - keys (minting rules):
    - `VoceRef`: composite (registrazioneId, voceId), typed kernel VO because >=2 contexts use it — correlation key of Attribuzione, ImprontaVocale and the Documento name map; stable as its parts
    - `ParlanteId`: minted by conferma-attribuzione (new Nome) and salta-voce via GeneratoreId (UUID v4) — stable across rinomina, promozione and eliminazione (tombstone keeps it); disappears only via INV-25 (occasionale left without Attribuzioni)

Sources: ADRs 0002, 0003, 0006, 0007, 0009, 0012 (.mismagent/decisions/); features/trascrizione-con-parlanti/architetture/architecture-overview.md (Supplier API NomiDelleVoci).
