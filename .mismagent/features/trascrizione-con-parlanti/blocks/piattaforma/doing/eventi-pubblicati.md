---
id: "eventi-pubblicati"
type: "port"
context: "piattaforma"
side: "app"
wave: 2
release: "R0"
module: "progetto|trascrizione|parlanti:applicazione (package ..applicazione.eventi)"
consumes:
  - "kernel-pl"
depends_on: []
related_adrs:
  - "0002"
  - "0003"
  - "0012"
  - "0014"
  - "0018"
owns_boundaries:
  eventi-progetto:
    projection: "in-process"
    contract_test: "consumer-driven"
    pinned_types:
      ProgettoCreato: "data class(progettoId: ProgettoId, nome: String) : EventoPubblicato"
      RegistrazioneAggiunta: "data class(registrazioneId: RegistrazioneId, progettoId: ProgettoId) : EventoPubblicato — AFTER-COMMIT consumers only (view refresh); NO synchronous subscriber (no automatic start on import, ADR 0014 / ADR 0012 Amendment (c))"
      DataRegistrazioneModificata: "data class(registrazioneId: RegistrazioneId, precedente: LocalDate, nuova: LocalDate) : EventoPubblicato — AFTER-COMMIT consumer: abbonato-documento"
      RegistrazioneRinominata: "data class(registrazioneId: RegistrazioneId, precedente: String, nuovo: String) : EventoPubblicato — precedente/nuovo = the titolo before/after RinominaRegistrazione (fix-batch-11, AC-360/361 in tasks/app/done/r0-feedback-1.md); AFTER-COMMIT consumers: AggiornamentiVista (avvio-r0, AC-366) and abbonato-documento (AC-186bis)"
  eventi-elaborazione:
    projection: "in-process"
    contract_test: "consumer-driven"
    pinned_types:
      ElaborazioneAvviata: "data class(registrazioneId: RegistrazioneId, avviataAlle: Instant) : EventoPubblicato"
      ElaborazioneCompletata: "data class(registrazioneId: RegistrazioneId) : EventoPubblicato"
      ElaborazioneFallita: "data class(registrazioneId: RegistrazioneId, motivo: String) : EventoPubblicato — motivo in plain Italian"
      TrascrittoSostituito: "data class(registrazioneId: RegistrazioneId) : EventoPubblicato — published ONLY in a completion transaction that replaced an existing Trascritto, BEFORE ElaborazioneCompletata (ADR 0018)"
      ElaborazioneAnnullata: "data class(registrazioneId: RegistrazioneId) : EventoPubblicato — published by AnnullaElaborazione in the cancelling transaction (a never-started in_attesa row deleted), AFTER COMMIT only; no synchronous subscriber (ADR 0018 Amendment (b))"
  eventi-revisione:
    projection: "in-process"
    contract_test: "consumer-driven"
    pinned_types:
      VociUnite: "data class(registrazioneId: RegistrazioneId, sopravvissuta: VoceId, rimossa: VoceId) : EventoPubblicato"
      VoceDivisa: "data class(registrazioneId: RegistrazioneId, origine: VoceId, nuova: VoceId, segmentiSpostati: List<SegmentoId>) : EventoPubblicato"
      SegmentoRiassegnato: "data class(registrazioneId: RegistrazioneId, segmentoId: SegmentoId, da: VoceId, a: VoceId, daRimossa: Boolean, aNuova: Boolean) : EventoPubblicato"
  eventi-parlanti:
    projection: "in-process"
    contract_test: "consumer-driven"
    pinned_types:
      AttribuzioneConfermata: "data class(voceRef: VoceRef, parlanteId: ParlanteId, precedente: ParlanteId?) : EventoPubblicato"
      ParlanteCreato: "data class(parlanteId: ParlanteId, progettoId: ProgettoId, nome: String, tipo: TipoParlanteVista) : EventoPubblicato"
      ParlanteRinominato: "data class(parlanteId: ParlanteId, nome: String) : EventoPubblicato"
      ParlantePromosso: "data class(parlanteId: ParlanteId, nome: String, nomeCambiato: Boolean) : EventoPubblicato"
      ParlanteEliminato: "data class(parlanteId: ParlanteId) : EventoPubblicato — NO Documento change"
      ImpronteRiallineate: "data class(registrazioneId: RegistrazioneId) : EventoPubblicato — published by riallinea-impronte after the commit of >= 1 refreshed print row (ADR 0012 Amendment (b)); consumers: proposta (cache invalidation), avvio-parlanti (AggiornamentiVista); NOT Documento (prints do not change it)"
      TipoParlanteVista: "enum RICORRENTE | OCCASIONALE (parlanti:applicazione)"
---
# eventi-pubblicati — Eventi pubblicati (Published Language degli eventi)

## What to do
Derived owner (rule 11) of every published event data class of the four event boundaries (eventi-progetto, eventi-elaborazione, eventi-revisione, eventi-parlanti — now incl. ImpronteRiallineate) plus TipoParlanteVista. Data classes only, implementing EventoPubblicato; the domain→published mapping pubblicato() stays with each service.

REWORK 2026-09-24 (ADR 0014): no behaviour change — RegistrazioneAggiunta has no synchronous subscriber any more (delivery re-pinned: after-commit only); fix the KDoc of progetto/applicazione/.../eventi/RegistrazioneAggiunta.kt that says it is delivered SYNCHRONOUSLY for the auto-start of the Elaborazione. The dispatcher's sync mechanism stays (ADR 0012).

REWORK 2026-09-24 (ADR 0018): add TrascrittoSostituito(registrazioneId) and ElaborazioneAnnullata(registrazioneId) in snastro.trascrizione.applicazione.eventi; shape tests (AC-14 now 17 events), sync-in-transaction vs after-commit delivery (AC-442, AC-470).

Note: AMENDED 2026-09-23 (ADR 0012 Amendment (b)): new published event ImpronteRiallineate(registrazioneId) in snastro.parlanti.applicazione.eventi (boundary eventi-parlanti). FOLLOW-UP REQUIRED: merged before ADR 0012 Amendment (b); the merged code does not yet satisfy the amended criteria above — a rework/fix block must land them. AMENDED 2026-09-24 (ADR 0018 + Amendment 2026-09-24 (b), manifest delta 2026-09-24-ritrascrivi): two new published events on eventi-elaborazione: TrascrittoSostituito (one SYNCHRONOUS subscriber: the Parlanti purge) and ElaborazioneAnnullata (after commit only).

## Tasks
- AC-14 Ogni evento pubblicato ha esattamente i campi e i tipi fissati nel suo boundary (un test di forma per ciascuno dei 17 eventi, inclusi ImpronteRiallineate, TrascrittoSostituito ed ElaborazioneAnnullata — REWORDED 2026-09-24, ADR 0018)
- AC-15 Tutti gli eventi pubblicati implementano EventoPubblicato e sono data class con soli val (Konsist CR-5)
- AC-442 snastro.trascrizione.applicazione.eventi.TrascrittoSostituito(registrazioneId: RegistrazioneId) : EventoPubblicato exists, with no other field; DispatcherEventiInMemoria delivers it to synchronous subscribers inside the publishing transaction, and to after-commit subscribers only after COMMIT, never on rollback (test with a synchronous and an after-commit recording subscriber on a rolled-back and on a committed transaction)
- AC-470 snastro.trascrizione.applicazione.eventi.ElaborazioneAnnullata(registrazioneId: RegistrazioneId) : EventoPubblicato exists with no other field; delivered to after-commit subscribers only after COMMIT, never on rollback

## Dependencies
- **eventi-progetto** (OWNED here — built before its consumers) — owner `eventi-pubblicati`, supplier `crea-progetto, servizi-registrazione, RinominaRegistrazione (progetto:applicazione, fix-batch-11)`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `ProgettoCreato`: data class(progettoId: ProgettoId, nome: String) : EventoPubblicato
    - `RegistrazioneAggiunta`: data class(registrazioneId: RegistrazioneId, progettoId: ProgettoId) : EventoPubblicato — AFTER-COMMIT consumers only (view refresh); NO synchronous subscriber (no automatic start on import, ADR 0014 / ADR 0012 Amendment (c))
    - `DataRegistrazioneModificata`: data class(registrazioneId: RegistrazioneId, precedente: LocalDate, nuova: LocalDate) : EventoPubblicato — AFTER-COMMIT consumer: abbonato-documento
    - `RegistrazioneRinominata`: data class(registrazioneId: RegistrazioneId, precedente: String, nuovo: String) : EventoPubblicato — precedente/nuovo = the titolo before/after RinominaRegistrazione (fix-batch-11, AC-360/361 in tasks/app/done/r0-feedback-1.md); AFTER-COMMIT consumers: AggiornamentiVista (avvio-r0, AC-366) and abbonato-documento (AC-186bis)
  - keys (minting rules):
    - `ProgettoId`: minted by crea-progetto via kernel GeneratoreId (UUID v4 string) — immutable; stored in progetto.db so it survives moving/copying the project folder
    - `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; also names audio/<id>.<ext>, cache/audio/<id>.wav and every EstrattoRef
  - delivery: All four events → in-process, AFTER COMMIT only (never on rollback), at-least-once, on a background coroutine, coalesced per registrazioneId; subscribers must be idempotent (INV-23); single writer per key (one process, one DB) so no cross-stream reordering hazard. AMENDED 2026-09-24 (ADR 0014 / ADR 0012 Amendment (c)): the SYNCHRONOUS clause for RegistrazioneAggiunta is dropped — it has no sync subscriber (the dispatcher's sync mechanism itself is unchanged, ADR 0012). AMENDED 2026-09-24 (delta 2026-09-24-rinomina-documento): RegistrazioneRinominata pinned (already published by the merged code)
- **eventi-elaborazione** (OWNED here — built before its consumers) — owner `eventi-pubblicati`, supplier `esegui-elaborazione (Avviata/Completata/Fallita/TrascrittoSostituito), annulla-elaborazione (ElaborazioneAnnullata)`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `ElaborazioneAvviata`: data class(registrazioneId: RegistrazioneId, avviataAlle: Instant) : EventoPubblicato
    - `ElaborazioneCompletata`: data class(registrazioneId: RegistrazioneId) : EventoPubblicato
    - `ElaborazioneFallita`: data class(registrazioneId: RegistrazioneId, motivo: String) : EventoPubblicato — motivo in plain Italian
    - `TrascrittoSostituito`: data class(registrazioneId: RegistrazioneId) : EventoPubblicato — published ONLY in a completion transaction that replaced an existing Trascritto, BEFORE ElaborazioneCompletata (ADR 0018)
    - `ElaborazioneAnnullata`: data class(registrazioneId: RegistrazioneId) : EventoPubblicato — published by AnnullaElaborazione in the cancelling transaction (a never-started in_attesa row deleted), AFTER COMMIT only; no synchronous subscriber (ADR 0018 Amendment (b))
  - keys (minting rules):
    - `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; also names audio/<id>.<ext>, cache/audio/<id>.wav and every EstrattoRef
  - delivery: in-process, AFTER COMMIT only (never on rollback), at-least-once, on a background coroutine, coalesced per registrazioneId; subscribers must be idempotent (INV-23); single writer per key (one process, one DB) so no cross-stream reordering hazard. EXCEPTION (ADR 0018/0012): TrascrittoSostituito has one SYNCHRONOUS subscriber (Parlanti purge, abbonato-revisione-parlanti → sostituzione-trascritto-policy) inside the publishing transaction; its other subscribers are after commit
- **eventi-revisione** (OWNED here — built before its consumers) — owner `eventi-pubblicati`, supplier `revisione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `VociUnite`: data class(registrazioneId: RegistrazioneId, sopravvissuta: VoceId, rimossa: VoceId) : EventoPubblicato
    - `VoceDivisa`: data class(registrazioneId: RegistrazioneId, origine: VoceId, nuova: VoceId, segmentiSpostati: List<SegmentoId>) : EventoPubblicato
    - `SegmentoRiassegnato`: data class(registrazioneId: RegistrazioneId, segmentoId: SegmentoId, da: VoceId, a: VoceId, daRimossa: Boolean, aNuova: Boolean) : EventoPubblicato
  - keys (minting rules):
    - `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; also names audio/<id>.<ext>, cache/audio/<id>.wav and every EstrattoRef
    - `VoceId`: minted by the trascritto aggregate from its persisted counter prossimaVoce — at creation 1..n in order of FIRST APPEARANCE (smallest turn inizioMs, tie: diarizer voceIndice); DividiVoce / riassegna-to-new take prossimaVoce++; never reused, never renumbered, == the n of the label 'Voce n'; stable for the life of one Trascritto GENERATION: a Ritrascrivi replacement (ADR 0018) is a fresh Trascritto.crea numbered from 1 again, and every VoceRef-keyed Parlanti row of the old generation is purged in the same transaction (TrascrittoSostituito)
    - `SegmentoId`: minted by the trascritto aggregate at creation only, 1..m in order (inizioMs, then voceId); no Segmento is ever created afterwards (INV-8) — stable for the Trascritto generation's life (ADR 0018: a replacement renumbers from 1)
  - delivery: Parlanti revisione-policy → in-process, SYNCHRONOUS inside the publishing command's UnitaDiLavoro transaction, in emission order, exactly once per commit attempt; an Esito.Errore or exception from a sync subscriber rolls the whole command back (ADR 0012). Documento / UI refresh / Parlanti RiallineaImpronte (abbonato-riallineamento-impronte) → in-process, AFTER COMMIT only (never on rollback), at-least-once, on a background coroutine, coalesced per registrazioneId; subscribers must be idempotent (INV-23); single writer per key (one process, one DB) so no cross-stream reordering hazard
- **eventi-parlanti** (OWNED here — built before its consumers) — owner `eventi-pubblicati`, supplier `conferma-attribuzione, salta-voce, gestione-parlante, riallinea-impronte`, projection in-process, contract_test **consumer-driven**
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

Sources: ADRs 0002, 0003, 0012, 0014, 0018 (.mismagent/decisions/); features/trascrizione-con-parlanti/tactical-model.md (Domain events of every context), ADR 0012.
