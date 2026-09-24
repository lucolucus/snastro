---
id: "identificazione-registrazioni"
type: "read-model"
context: "parlanti"
side: "app"
wave: 5
release: "R2"
module: ":parlanti:applicazione (..letture)"
consumes:
  - "kernel-pl"
  - "agg-attribuzione"
  - "repo-parlanti"
  - "voci-per-parlanti"
depends_on: []
related_adrs:
  - "0002"
  - "0003"
  - "0006"
  - "0007"
  - "0009"
  - "0012"
  - "0019"
view_shape:
  conteggi: "List<{registrazioneId, numVociDaIdentificare}>"
view_sources:
  conteggi: "← voci-per-parlanti (number of Voci) − AttribuzioneRepository.diRegistrazione (attributed)"
---
# identificazione-registrazioni — Voci da identificare per Registrazione — fetta Parlanti (S2)

## What to do
Parlanti slice of the S2 badge (R1 split); only Registrazioni with a Trascritto.

### view_shape (field ← source)
- `conteggi`: List<{registrazioneId, numVociDaIdentificare}> ← ← voci-per-parlanti (number of Voci) − AttribuzioneRepository.diRegistrazione (attributed)

## Tasks
- AC-166 La vista espone registrazioneId e numVociDaIdentificare = Voci meno Voci attribuite; Registrazioni senza Trascritto non compaiono

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

Sources: ADRs 0002, 0003, 0006, 0007, 0009, 0012, 0019 (.mismagent/decisions/); features/trascrizione-con-parlanti/UI/ux-proposal.md S2 (+ R1).
