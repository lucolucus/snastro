---
id: "avvio-coda-condivisa"
type: "adapter"
context: "piattaforma"
side: "app"
wave: 2
release: "R3"
module: ":avvio (CodaElaborazioni → CodaCondivisa, r1/r2 wiring) + sweep: :trascrizione:applicazione ..letture (StatoRegistrazioneVista, StatiElaborazione), :ui snastro.ui.registrazioni (S2 presenter)"
consumes:
  - "elaborazioni-in-coda"
  - "posizioni-nella-coda"
reuses:
  - "trascrizione-con-parlanti/kernel-pl"
  - "trascrizione-con-parlanti/tec-modelli"
related_adrs:
  - "0004"
  - "0012"
  - "0018"
  - "0023"
model_hint: "deep"
tests_nl_status: "confirmed"
---
# avvio-coda-condivisa — Coda FIFO condivisa multi-sorgente (CodaCondivisa) + PosizioniNellaCoda + rimozione di posizioneInCoda

## What to do
Generalize and rename CodaElaborazioni into CodaCondivisa over N sources (FonteCoda = FonteAvanzamento + teste(esclusi) + recupera()), with the total order (istante, tipo, id), the bounded claim through each context's own command, holding on missing sherpa models, per-source exclusion and recovery, and the best-effort cancellation hook; implement :ui's PosizioniNellaCoda. R1/R2 compositions bind only the Elaborazione source (behaviour unchanged). SWEEP owned here (ADR 0023 enforced_by exigible_from this block): remove StatoRegistrazioneVista.posizioneInCoda and StatiElaborazione's rank; S2's presenter reads the position from PosizioniNellaCoda.

Note: REWORK of trascrizione-con-parlanti code (avvio-coda-elaborazioni, stati-elaborazione, schermata-registrazioni). PINNED REQUIREMENT kept: single-thread dispatcher (AC-314) — the in_attesa → in_corso transitions of BOTH kinds rely on it. The Riassunto source is bound by avvio-sintesi (R3); here it is exercised with fake sources only. POST-REBASE TASK (R19-5, user 2026-09-25): once rebased, add the superseded-by pointer on the sibling's AC-162/163/474 (see post_rebase_tasks).

## Tasks
_tests_nl status: CONFIRMED by the user at the rule-5 checkpoint (2026-09-25)._

- AC-S55 The existing CodaElaborazioni tests (AC-233, 234, 235, 312, 313, 314) run green against CodaCondivisa with only the Elaborazione source bound
- AC-S56 Strict FIFO across kinds (fake sources): E@t1, R@t2, E@t3, R@t4 all in_attesa → the run order is E1, R2, E3, R4; an item enqueued while another runs takes its place by instant
- AC-S57 Tie: an Elaborazione and a Riassunto with the same millisecond → the Elaborazione runs first; two items of the same kind keep their own source order (id)
- AC-S58 Bound: claiming the Elaborazione head passes nonDopo = the Riassunto head's instant (null when that source is empty); claiming the Riassunto head passes primaDi = the Elaborazione head's instant
- AC-S59 Head vanished between peek and claim (the claimed source answers Nessuno) → the tick re-evaluates and the next item in global order runs; no item of the other source is overtaken
- AC-S60 Holding: the global head is an Elaborazione and the sherpa models are not pronti → NOTHING runs, including a Riassunto queued behind it; when the models become pronti the queue resumes in order. A Riassunto head is never held for the LLM model
- AC-S61 recupera() runs for every source at start (before the first claim) and after anything escapes a run; a stuck head is excluded per source id (after the AC-313 attempts) while the other items of both sources keep draining
- AC-S62 PosizioniNellaCoda.istantanea(): positions are 1-based over ALL in_attesa items of both sources in the global order, the in_corso item is not counted, keyed by registrazioneId per kind (E@t1 in_attesa, R@t2, E@t3 → elaborazioni {r1:1, r3:3}, riassunti {r2:2})
- AC-S63 annullaInCorso(tipo, registrazioneId) flips the annullato flag passed to the running item only if it is of that kind and Registrazione; otherwise no effect. fermaEAttendi flips annullato and interrupts the worker
- AC-S64 SWEEP: StatoRegistrazioneVista has no posizioneInCoda; S2 shows 'In coda (2)' / 'Ritrascrizione in coda (2)' from PosizioniNellaCoda.istantanea().elaborazioni (presenter test with the fake); ADR 0023 enforced_by exits 0. Supersedes trascrizione-con-parlanti AC-163 and the posizioneInCoda half of AC-162 / AC-474 (their tests move here)

## Dependencies
- **coda-condivisa** (OWNED here; owner avvio-coda-condivisa; projection in-process; contract_test `consumer-driven`)
  - `FonteCoda (internal, snastro.avvio)`: class(tipo: TipoElementoCoda, teste: (esclusi: Set<String>) -> ElementoInCoda?, prossima: (esclusi: Set<String>, limite: Instant?) -> RisultatoTentativo, ultimaTentata: () -> String?, recupera: () -> Unit, trattenuta: () -> Boolean /* true = hold the whole queue while this source's head waits (sherpa models not pronti) */)
  - `ElementoInCoda`: data class(id: String, registrazioneId: String, istante: Instant)
  - `TipoElementoCoda`: enum { ELABORAZIONE /* 0 */, RIASSUNTO /* 1 */ } — ordinal is the tie-breaker
  - `CodaCondivisa`: class(scope, fonti: List<FonteCoda>, segnalaBloccato: (String) -> Unit, …) { fun avanza(); fun annullaInCorso(tipo: TipoElementoCoda, registrazioneId: String); fun fermaEAttendi(timeoutMs): Boolean; : PosizioniNellaCoda }
  - `RisultatoTentativo`: existing sealed { Nessuno; Avviata(id); Rifiutata(id) }
  - key `order`: (istante, tipo.ordinal, id) — total, deterministic; a single-thread dispatcher (AC-314) claims; the bound passed to a claim = the other source's head instant
  - delivery: in-process, single worker thread; signals coalesced (avanza) + 1 s re-arm; recupera() per source at start and after an escape
- **kernel-pl** (REUSED — boundary `kernel-pl` of features/trascrizione-con-parlanti/building-blocks.yaml, owner `kernel` already integrated on main; not redeclared in this manifest; projection in-process; contract_test `consumer-driven`)
  - `Published Language (unchanged)`: RegistrazioneId, ProgettoId, SegmentoId, VoceId, VoceRef(registrazioneId, voceId), IntervalloMs, Esito, ErroreDominio, EventoPubblicato, Creato, GeneratoreId, UnitaDiLavoro (+ testFixtures UnitaDiLavoroFinta.transazioneAperta), DispatcherEventi — as pinned in features/trascrizione-con-parlanti/building-blocks.yaml boundary kernel-pl
  - key `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; the correlation key of every Sintesi row
  - key `SegmentoId / VoceId`: minted by the trascritto aggregate per Trascritto GENERATION (ADR 0018: a replacement renumbers from 1) — stable for the generation's life; Sintesi stores them only inside a Riassunto that is deleted with its generation (INV-S8)
- **elaborazioni-in-coda** (consumed; owner coda-trascrizione-delta; projection in-process; contract_test `consumer-driven`)
  - `ElaborazioniInAttesa (snastro.trascrizione.applicazione.letture)`: class { fun elenco(): List<ElaborazioneInCoda> } — FIFO (creataAlle, id), in_attesa only
  - `ElaborazioneInCoda`: data class(elaborazioneId: String, registrazioneId: RegistrazioneId, creataAlle: Instant)
  - `EseguiProssimaElaborazione`: data class(esclusi: Set<ElaborazioneId> = emptySet(), nonDopo: Instant? = null) — claims its oldest eligible head only if creataAlle ≤ nonDopo, head read inside its BEGIN IMMEDIATE transaction
  - key `elaborazioneId`: ElaborazioneId.valore minted by avvia-elaborazione (UUID v4) — the queue's exclusion key for the Elaborazione source
  - key `creataAlle`: minted by AvviaElaborazione from the injected clock (epoch millis) — the source's FIFO key, orderable
- **posizioni-nella-coda** (consumed; owner posizioni-nella-coda; projection in-process; contract_test `consumer-driven`)
  - `PosizioniNellaCoda (snastro.ui.coda)`: interface { fun istantanea(): PosizioniCoda }
  - `PosizioniCoda`: data class(elaborazioni: Map<RegistrazioneId, Int>, riassunti: Map<RegistrazioneId, Int>) { companion VUOTA } — 1-based over ALL in_attesa items of both kinds in the global order; in_corso not counted
  - key `RegistrazioneId`: exact per kind: at most one open item per Registrazione per kind (INV-4, INV-S2)
- **tec-modelli** (REUSED — boundary `tec-modelli` of features/trascrizione-con-parlanti/building-blocks.yaml, owner `modelli-provisioning` already integrated on main; not redeclared in this manifest; projection in-process; contract_test `consumer-driven`)
  - `(unchanged)`: as pinned in features/trascrizione-con-parlanti/building-blocks.yaml boundary tec-modelli; extended by tec-modelli-facoltativo
- coda-trascrizione-delta — build dependency (merged before this block)
- posizioni-nella-coda — build dependency (merged before this block)

Sources: ADR 0023 §1–5 + enforced_by, architecture-overview D-7/D-8; related_adrs 0002, 0004, 0008, 0012, 0018, 0023; tactical-model: features/sintesi/tactical-model.md
