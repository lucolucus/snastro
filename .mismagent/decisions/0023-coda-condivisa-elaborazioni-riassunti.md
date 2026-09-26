---
scope: global
status: accepted
supersedes: null   # partial, amended in place with dated pointers here: ADR 0004 ("one Elaborazione at a time from a serial FIFO queue" → one item at a time from a SHARED FIFO), architecture.md § Pipeline and progress, ADR 0018 Amendment (b) (S2's position counts Riassunti ahead)
closes_spike: null
enforced_by:   # migrated 2026-09-26 (mismAgent 0.22) from the legacy inline shell rule: same grep/find logic, now versioned checks run by the gate (architettura-test ControlliAdrTest, red-green on fixture/<check>/)
  - check: architettura-test/controlli-adr/adr-0023-posizione-in-coda-fuori-dai-contesti.sh
    from: avvio-coda-condivisa
  # legacy note: block id proposed here, pinned by build-manifest. Validated 2026-09-25 via bash -c: tree exit 1 — red BY DESIGN (StatiElaborazione/StatoRegistrazioneVista still compute posizioneInCoda in Trascrizione; this ADR moves it to :avvio); green once that block removes the field
---
# 0023 — One shared FIFO queue for Elaborazioni and Riassunti, owned by `:avvio`; the queue position is computed by the owner

## Context
The user decided (2026-09-25) that `Elaborazione`s and `Riassunto`s run **one at a time, strictly by
request instant**, in ONE serial queue: two heavy native workloads never run together (the ML
pipeline, ADR 0004/0011, and a 9B LLM). While the LLM model is not installed, no `Riassunto` is
created (Q-S1 "download only"), so the queue stays **strict FIFO**, with no skip exception.

Facts in the code:
- `CodaElaborazioni` (`:avvio`) is already generic over primitive ids (`FonteAvanzamento`,
  `RisultatoTentativo`): a single-thread dispatcher, coalesced `avanza()`, recovery first, and
  per-id exclusion of a stuck head (AC-312/313).
- `EseguiProssimaElaborazione(esclusi)` claims the oldest eligible `in_attesa` inside its
  transaction (AC-314). It is serialized against cancellation by `BEGIN IMMEDIATE`
  (ADR 0018 (b) §3).
- `StatiElaborazione` (Trascrizione) computes S2's `posizioneInCoda` as a rank over **its own**
  `in_attesa` rows. Trascrizione must not read Sintesi rows (ADR 0021 §8), so it cannot count the
  `Riassunto`s ahead.

## Decision

### 1. The owner: `:avvio`'s queue becomes multi-source (`CodaCondivisa`)
`CodaElaborazioni` is generalized, and renamed in the same block, into a queue over **N sources**,
each a `FonteAvanzamento` extended with:
- `teste(esclusi): ElementoInCoda?`, the source's oldest eligible `in_attesa` item as
  `(id: String, registrazioneId: String, istante: Instant)`;
- `recupera()`, its startup/escape recovery.

v1 has two sources:
- **Elaborazione**, over a new public query `ElaborazioniInAttesa` in `:trascrizione:applicazione
  ..letture`: `(elaborazioneId, registrazioneId, creataAlle)` in FIFO order;
- **Riassunto**, over `RiassuntiInAttesa` (ADR 0021): `(riassuntoId, registrazioneId, richiestoAlle)`.

The queue stays context-agnostic (primitive ids only). The R3 composition binds the two sources, and
R0–R2 bind only the Elaborazione source (behaviour unchanged).

### 2. Total order and the claim
- **Order key:** `(istante, kind, id)`, with kind `Elaborazione` (0) before `Riassunto` (1) on an
  equal millisecond. It is deterministic and never re-orders two items of the same kind.
- **One tick** on the single worker thread:
  1. read both heads (outside any transaction);
  2. pick the smaller by the key;
  3. claim **through that context's own command**, passing the other head's instant as a bound:
     - `EseguiProssimaElaborazione(esclusi, nonDopo: Instant?)`: the claim takes its oldest eligible
       head **only if** `creataAlle ≤ nonDopo`;
     - `EseguiProssimoRiassunto(esclusi, primaDi: Instant?)`: only if `richiestoAlle < primaDi`.
     
     Each claim reads its head **inside** its own `BEGIN IMMEDIATE` transaction (AC-314 unchanged).
     If its head vanished (cancelled or deleted) and the next one would jump the other source's
     head, it returns `Nessuno`, and the tick re-evaluates.
  
  This keeps strict FIFO across the two sources without either context reading the other's rows.
  An item queued between the peek and the claim is newer than both heads, so it cannot break the
  order.
- **Holding** (AC-235 generalized): if the head is an `Elaborazione` and the required sherpa models
  are not `pronti()`, the **whole** queue holds, including a `Riassunto` behind it (strict FIFO). In
  practice that state only exists during onboarding (ADR 0008).

  A `Riassunto` head is **never** held for the LLM model: it is claimed, and ends `fallito`
  `modello_non_disponibile` if the model was removed after the request (Q-S1).
- **Exclusion of a stuck head** (AC-313) applies per source id, unchanged. `recupera()` runs
  **for every source** at start (AC-233) and after an escape (AC-312):
  `RecuperaElaborazioniInterrotte` + `RecuperaRiassuntiInterrotti` (an `in_corso` `Riassunto` with
  no live run → `fallito` `interrotto`; `in_attesa` stay queued).
- **Signals:** `avanza()` on `RiassuntoRichiesto`, as today for `ElaborazioneAvviata`/queued. The
  existing 1 s re-arm also covers it.

### 3. Running a `Riassunto` (`EseguiProssimoRiassunto`)
Three phases, with **no transaction around the LLM**:
1. **Claim:** a short transaction, `in_attesa → in_corso`, then `RiassuntoAvviato`.
2. **Run, outside any transaction:**
   - read the `Segmento`s (`LettoreTrascritto`) and the current names (`LettoreNomi`);
   - build the labelled input (`IngressoRiassunto`);
   - call `ModelloLinguistico.riassumi(…, annullato)`;
   - apply [INV-S4] on the root against the structure read in this phase.
3. **Complete:** the compare-and-set of ADR 0022 §4, which commits `pronto` (replacing) or
   `fallito`, or nothing at all.

The LLM runs on the queue's dedicated worker thread (`runInterruptible`, as the pipeline does).

### 4. The queue position: computed by the owner, joined in the presenter
- `:ui` declares a new port **`PosizioniNellaCoda`**, implemented in `:avvio` by the queue (the same
  pattern as `ServizioModelli`, ADR 0008 R10):
  `istantanea(): PosizioniCoda(elaborazioni: Map<RegistrazioneId, Int>, riassunti: Map<RegistrazioneId, Int>)`.
  - A position is 1-based over **all** `in_attesa` items of both sources in the §2 order. An item
    `in_corso` is not counted, the same meaning AC-163 has today.
  - Keying by `registrazioneId` is exact: at most one open item per `Registrazione` per kind
    ([INV-4], [INV-S2]).
- **S2**'s presenter takes "In coda (n)" / "Ritrascrizione in coda (n)" from
  `istantanea().elaborazioni`.
- **The Riassunto tab**'s presenter takes "In coda · n° nella coda" from `istantanea().riassunti`.
- **Arbitration of the consumer-driven view shape** (ux-proposal § Data views):
  - `riassunto-vista`'s `InAttesa` carries `richiestoAlle`, **not** `posizioneInCoda`. The position
    is not Sintesi's data, and a Sintesi read-model computing it would have to read Trascrizione rows.
  - Likewise `stati-elaborazione`'s `StatoRegistrazioneVista.posizioneInCoda` is **removed**, so
    there is one source of truth. That is the `enforced_by` above.
  - What the user sees is unchanged, except that it now counts across both kinds, as the user asked.
- Refresh: S2 already reloads on every `Cambiamento`, and the Sintesi events emit
  `Cambiamento(registrazioneId)` (ADR 0021 §3), so every position is re-read when either kind moves.

### 5. LLM execution discipline
- **Never inside a transaction** (ADR 0012). The `ModelloLinguisticoFinto` transaction guard is the
  mechanical test (ADR 0021 §4).
- **Cancellation** (best effort): on `RiassuntoEliminato` (after commit), `:avvio` checks whether
  the running item is a `Riassunto` of that `registrazioneId`. If so, it flips the `annullato` flag
  passed to that run. Correctness does not depend on the cancellation: the completion's
  compare-and-set writes nothing for a deleted row ([INV-S8]). Cancelling only frees the queue
  sooner.
- **Stop** (`fermaEAttendi`, 5 s): the queue flips `annullato` and interrupts the worker. The spike
  ADR states whether the runtime meets the 5 s bound. If it does not, the worker thread is a daemon
  and the next start's recovery marks the row `interrotto`.
- **The native Mutex of ADR 0012 (b)/0017 is NOT taken by the LLM.** It serializes **sherpa** calls
  (non-reentrant native state). The LLM is a different runtime with no shared native state, and the
  shared queue already keeps it apart from the pipeline.

  A `Conferma`/`Proposta` extraction may therefore run while a `Riassunto` runs, instead of waiting
  up to ~3 min behind it (ADR 0017's user-facing-wait concern). Memory: ~7–8 GB for the LLM plus
  ~1 GB for an extraction, within the reference machine. The spike measures peak RSS. If it shows
  contention, its ADR may revisit this point. That would be a targeted amendment, not a reopening.
- **Memory release:** the adapter unloads the model after each run, unless the spike ADR decides a
  keep-warm policy (ADR 0021 §5). The next queued `Elaborazione` must not inherit ~7 GB of LLM memory.

### 6. NFR: time (a verifiable constraint, not words)
- **A `Riassunto` of a 60-minute `Registrazione` (≈ 25 k input tokens) completes in ≤ 300 s** on the
  reference machine (Apple M3 Pro, 36 GB), from `RiassuntoAvviato` to `RiassuntoPronto`.
  - This is a measurable AC on the real `ModelloLinguistico` adapter block (`@Tag("modelli")`, opt-in,
    outside the gate, like ADR 0011).
  - The runtime spike's closure criterion (Via Roquel, 175 s ± 20 %) is its first measurement.
  - An opt-in `./gradlew benchmarkRiassunto -Pcampione=<fixture>` records it again later.
- The tab's "circa 3 minuti per un'ora" text is provisional until that measurement.

## Rejected options
- **Two queues** (one per kind) with a global lock. This gives no strict FIFO across kinds, and
  the user chose one queue.
- **Trascrizione counting `Riassunto`s** (reading Sintesi rows, or a Sintesi port inside
  Trascrizione). It inverts the relationship: Trascrizione is upstream and does not know Sintesi
  exists (context-map).
- **A queue table shared by both contexts** (a `coda` row per item). It is a third owner of
  per-context state, and every enqueue and cancel would touch two tables in two contexts.
- **Letting a `Riassunto` jump ahead while the sherpa models are missing.** The user chose strict
  FIFO (Q-S1), and that state is onboarding only.
- **Taking the sherpa Mutex around the LLM.** It would make every `Conferma` wait for a whole
  `Riassunto` (minutes), for no shared-state reason.

## Consequences
- **Trascrizione changes** (application layer only; no aggregate, invariant or event changes, so
  the tactical model is untouched):
  - `EseguiProssimaElaborazione` gains `nonDopo: Instant? = null`;
  - new query `ElaborazioniInAttesa`;
  - `StatoRegistrazioneVista.posizioneInCoda` is removed, and S2 reads `PosizioniNellaCoda`.
  
  These are pinned as a manifest delta by build-manifest.
- `:avvio`: `CodaElaborazioni` becomes `CodaCondivisa`. Block `avvio-coda-condivisa` is proposed,
  and its existing AC-233/234/235/312/313/314 tests are re-run for the Elaborazione source unchanged.
- **Enforcement:**
  - `enforced_by` above: no `posizioneInCoda` is computed in Trascrizione or Sintesi. Red by design
    until `avvio-coda-condivisa`.
  - Test: the fake-transaction guard of `ModelloLinguisticoFinto`; the queue's order tests (an
    interleaved E/R/E/R FIFO; an equal-millisecond tie; a head deleted between the peek and the
    claim; a `Riassunto` behind a held `Elaborazione`).
  - Discursive (code review): the claim reads its head inside its transaction; the bound is honoured
    inside the claim, not only by the coordinator; no Mutex acquisition around the LLM.
