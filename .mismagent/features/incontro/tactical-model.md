# Tactical model — incontro

> Canonical names come from `.mismagent/context-map.md` (amended 2026-09-30 by feature `incontro`);
> this file never renames them. Decisions: [decisions.md](decisions.md) D-0001..D-0004, D-0006..D-0010
> (D-0005 superseded by D-0008); brief: [product-brief.md](product-brief.md). Single side (`app`):
> every boundary is in-process (port + contract test), no OpenAPI.
> Invariant ids are local to this file: prefix `[INV-I…]`. `INV-1…28` are trascrizione-con-parlanti's,
> `INV-S1…S10` are sintesi's; where this file amends one of them it says so and quotes the new rule.
> `[user]` = decided by the user (D-nnnn or the model checkpoint of 2026-10-01); `[mod]` = decided here by
> the tactical modeler inside what the map leaves open (each is a DECISIONS entry for the conductor).
> Numbering for the architect: next free ADR **0033** (0031, 0032 used on main); next migration **`7.sqm`**.
> Spikes: `contesto-lungo` CLOSED (D-0010, one pass). `voci-tra-parti` OPEN → node
> [tasks/app/backlog/voci-tra-parti.md](tasks/app/backlog/voci-tra-parti.md) (blocks ONLY the print-based
> cross-Parte proposal). `ora-di-inizio` OPEN → node
> [tasks/app/backlog/ora-di-inizio.md](tasks/app/backlog/ora-di-inizio.md) (blocks ONLY reading the start
> time from the file's metadata at import).

## Seeds for the tactical
<!-- Absorbed by mismagent-tactical-modeler on 2026-10-01 into the sections below (INV-I1..I13 kept with their
     ids; the open points resolved as [mod] or listed for the architect). Empty. -->

## Seam granularity (what crosses a context boundary — modeled decisions)
- **`Incontro` membership (Progetto → Trascrizione, Parlanti, Sintesi):** crosses as ONE unit keyed by
  `incontroId`: the **ordered** list of its `Parte`s, each `registrazioneId` with `DataRegistrazione`,
  `OraDiInizio?` and its numero della parte (derived by [INV-I2] at read time, never stored). Every reader
  gets the order from Progetto through its OWN port; no reader re-computes it. Readers never write it.
- **`Voce` (Trascrizione → Parlanti, Sbobinatura, Sintesi):** crosses as ONE unit keyed by
  **`VoceRef = (incontroId, voceId)`** [user D-0002], no longer `(registrazioneId, voceId)`. `voceId` IS the
  "Voce n" number (R7), unique in the `Incontro` and never reused there ([INV-I4]). It never carries a
  `Parte`: "the part of a `Voce` inside one `Parte`" is its `Segmento`s in that `Parte` (map, `Voce`).
- **`Segmento` (Trascrizione → Parlanti, Sbobinatura, Sintesi):** crosses as ONE unit keyed by
  `(registrazioneId, segmentoId)` — unchanged key — carrying `voceId` (an `Incontro` number), `inizio`,
  `fine` (ms from the start of THAT file) and, towards Sbobinatura and Sintesi only, the text.
  `segmentoId` is never reused within its `Registrazione`, **across `Trascritto` generations too**
  ([INV-I16], [mod]).
- **`Fonte` (inside Sintesi):** ONE `(registrazioneId, segmentoId)` — a unit, kept by the `Registrazione`'s
  identity, never by the numero della parte [map]. An element holds a SET of `Fonte`s.
- **`ImprontaVocale` (inside Parlanti):** ONE unit per `(Parlante, VoceRef, Parte)` [INV-I8]: it is sourced
  from the `Segmento`s of one `Voce` in ONE `Parte` and records that `Parte`'s `registrazioneId`. It is
  never aggregated across `Parte`s or averaged.
- **Trascrizione state (→ Sintesi):** per `Parte`: "a `Trascritto` exists", "an `Elaborazione` is open".
  Sintesi folds them over the `Incontro`'s `Parte`s itself.
- No quantity enters a conserved invariant. The only conservation is still the set of `Segmento`s under
  `Revisione` ([INV-8], now over the whole `Incontro`).

## Tactical model — Progetto — every row names the consumer, or it is not written
- **Aggregates / entities:**
  - `Incontro` (root, NEW; identity `incontroId`, references `progettoId`, immutable) — a thin root: it
    guards only its identity and `Progetto`. Order, title, date, numero della parte and "· N parti" are
    **derived at read time, never stored** [map]. It exists so that the `Voce`s, the `Attribuzione`s and the
    `Riassunto` have a key to reference (FKs: architect).
    → manifest `aggregate` block (incontro) + migration `7.sqm` (architect)
  - `Registrazione` (root, amended) additionally guards `incontroId` (set at import, **immutable**, like
    `progettoId` in INV-1) and `OraDiInizio` (optional VO, user-editable) [user D-0008, D-0009].
    → `aggregate` block (registrazione, amended)
  - `OraDiInizio` = VO: an optional time of day (to the second, local wall-clock as the file gives it, no
    time zone). Empty = unknown. [mod]
    → VO table test on the registrazione block
- **Invariants:**
  - [INV-I1] Every `Registrazione` is a `Parte` of exactly one `Incontro` of the SAME `Progetto`; its
    `incontroId` is set at import and never changes. An `Incontro` has ≥ 1 `Parte`: it is created only by
    the import that brings its first `Parte`s, **in the same transaction**, and it ceases to exist in the
    transaction that eliminates its last `Parte`. An empty `Incontro` never exists.
    → set rule: tests on the aggiungi-registrazione and elimina-registrazione application-service blocks
    + invariant test on registrazione (immutable `incontroId`)
  - [INV-I2] The `Parte`s of an `Incontro` are in a **total** order by the key
    (`DataRegistrazione`, `OraDiInizio` **empty last**, `aggiunta_alle`, `registrazioneId`) [user 2026-10-01
    "empties and ties fall back to import order, then id"; "empty last" is [mod]]. The numero della parte
    is the 1-based rank in that order. *Why "empty last" and not "compare the time only when both have one":
    that rule is not transitive (A 09:00 imported 3rd, B empty imported 2nd, C 10:00 imported 1st gives
    A<C<B<A), so the order would depend on the sort algorithm.*
    → a pure ordering function in Progetto's domain (table test) + view test on every read-model that
    shows the `Parte`s
  - [INV-I3] A 1-part `Incontro` looks like today's `Registrazione`: same views, no "parte" label, no
    "· N parti", same data after the migration. The only deliberate differences are D-0007: `Voce` numbers
    are never reused ([INV-I4]) and a `Ritrascrivere` makes the `Riassunto` `superato` instead of deleting and
    re-enqueuing it. → view test on every read-model touched + the `7.sqm` migration test (a pre-feature DB:
    same `Voce` numbers, same `Attribuzione`s, same `Riassunto` NOT `superato`)
  - [INV-I14] `OraDiInizio`, when present, is a valid time of day in [00:00:00, 24:00:00). Empty is a
    legal value, also after an edit (the user may clear it). [mod]
    → VO table test on the registrazione block
- **Domain events:**
  - `RegistrazioneAggiunta` *(amended: + `incontroId`)*, one per imported file → `read-model` Incontri del
    Progetto (list) + `read-model` of the `Incontro` page (its `Parte`s) + Sintesi `riassunto-vista` refresh
    (a new `Parte` makes a `pronto` `Riassunto` `superato`, derived — [INV-I11]; no Sintesi write).
    *No separate "Incontro created" event: it would have no reader beyond the list refresh that
    `RegistrazioneAggiunta` already triggers.* [mod]
  - `DataRegistrazioneModificata` *(amended: + `incontroId`)* → the same read-models (reorder) + Sintesi
    view refresh (reorder ⇒ `superato`, derived) + Sbobinatura `Rigenerazione` of that `Parte` only (its file
    name has the date, R5, unchanged). "Ospite del …" does NOT follow it (INV-19 stored name, unchanged).
  - `OraDiInizioModificata` (registrazioneId, incontroId) (NEW) → the same read-models (reorder) + Sintesi
    view refresh. NOT Sbobinatura (the time is in no `.md`).
  - `RegistrazioneEliminata` *(amended: + `incontroId` + whether the `Incontro` ceased with it, i.e. it was
    its last `Parte`)* → Trascrizione policy (synchronous, [INV-I6]), Sintesi policy (synchronous, [INV-I12b]),
    Sbobinatura removal + `:avvio` cleanup (after commit, unchanged). The Parlanti purge now reaches Parlanti
    through Trascrizione's Voce-level event (see Trascrizione) — **for the architect** (ADR 0020 §2, ADR 0030
    order).
- **Commands (+ actor):**
  - `AggiungiRegistrazione` *(amended)* (actor: utente — "Importa" on the `Progetto` into a NEW `Incontro`,
    or on an opened `Incontro` into THAT `Incontro`) [user D-0008]: one or several files, and a target that is
    either "new `Incontro`" or an existing `incontroId` of the same `Progetto` (else `IncontroNonTrovato`,
    nothing written). ONE transaction: create the `Incontro` if new, then one `Registrazione` per file with
    that `incontroId`, `titolo` by the unchanged rules (uniqueness also among the files of the same import),
    `OraDiInizio` read from the file when the reading rule of spike `ora-di-inizio` gives one, else empty.
    **All or nothing** [mod]: if any file cannot be imported, no `Registrazione` and no new `Incontro` are
    written and the copies already made are scartate (the existing internal compensation). No `Elaborazione`
    starts (ADR 0014, unchanged).
    → `application-service` block (aggiungi-registrazione, amended); the metadata reading is a separate
    adapter block blocked by spike `ora-di-inizio`; until it closes the adapter returns empty.
  - `ModificaOraDiInizio` (registrazioneId, ora?) (NEW; actor: utente, beside `ModificaDataRegistrazione`)
    [user D-0009] → `application-service` block. Same value → no event.
  - `ModificaDataRegistrazione` unchanged, event amended.
  - `EliminaRegistrazione` *(amended)*: unchanged precondition ([INV-28] veto on an open `Elaborazione` of
    THAT `Parte` only). In the same transaction, after the subscribers and the `registrazione` row, the
    `Incontro` row is removed iff no `Parte` is left ([INV-I1]).
  - There is no attach, separate, merge or move command [user D-0008].
- **Policy:** none new in Progetto.

## Tactical model — Trascrizione — every row names the consumer, or it is not written
- **Aggregates / entities:**
  - `Elaborazione` (root, one per run, per `Registrazione`) — unchanged: [INV-3], [INV-4] per `Registrazione`,
    `NumeroPersone` immutable per run (ADR 0014).
  - **Voci dell'Incontro** (root, NEW; identity `incontroId`) [mod] — the "Voci dell'Incontro" of the map
    (`Voce`). It is the consistency boundary of `Revisione`, because a `Voce` now spans `Parte`s. It guards:
    - the counter of `Voce` numbers of the `Incontro` ([INV-I4]);
    - one `Trascritto` **entity per transcribed `Parte`** (identity `registrazioneId`; its `Segmento`s with id,
      interval, text, `Voce`, `confermato`; its own `segmentoId` counter, [INV-I16]). The `Trascritto` stays
      one per `Registrazione` [map]; it is no longer a root of its own;
    - the `Voce`s, derived as today from the `Segmento` → `Voce` assignment (a `Voce` exists iff it has ≥ 1
      `Segmento` in some `Parte`).

    The `Revisione` methods (`unisci`, `dividi`, `riassegna`, `riassegnaInBlocco`, `confermaSegmento`) move to
    this root, plus `completaParte` (first transcription or replacement of one `Parte`) and `rimuoviParte`.
    It is created by the first completion of any `Parte` of the `Incontro` and removed only when the
    `Incontro` ceases to exist — **not** when its last `Trascritto` goes — so the counter survives
    ([INV-I4]).
    *Why one root and not two (a per-`Parte` `Trascritto` root + a per-`Incontro` assignment root): the
    `Revisione` checks need the intervals and the flags (INV-7 order, `riassegnaInBlocco`'s stale-plan check),
    so a split root would either duplicate them or load both in every command; a 3 h `Incontro` is ≈ 3 000
    `Segmento`s in memory, the same order as today's 1 h × 3.*
    → manifest `aggregate` block (voci-dell-incontro; the trascritto block is reworked into it) + architect
    decision (persistence: `voce` re-keyed by `incontro_id`, the counter row per `Incontro`, `trascritto`/
    `segmento` stay per `registrazione_id`; `7.sqm`)
- **Invariants** (INV-3, INV-4, INV-7, INV-9, INV-10, INV-11, INV-26 unchanged in wording, with "the same
  `Trascritto`" read as "the same `Incontro`"):
  - [INV-5] *(amended)* a `Parte`'s `Trascritto` exists iff that `Registrazione` has ≥ 1 `completata`
    `Elaborazione`; it is written (created or replaced whole) atomically with each transition to `completata`,
    inside the Voci dell'Incontro root ([INV-I5]). `Revisione` and every Parlanti operation on a `Voce` need
    the `Voce` to exist, hence ≥ 1 transcribed `Parte`.
    → esegui-elaborazione application-service block
  - [INV-6] *(scope)* every `Segmento` belongs to exactly one existing `Voce` of the `Incontro`; every `Voce`
    has ≥ 1 `Segmento`, in ANY `Parte`. A `Voce` left with none (by `unire`, `riassegnare`, a replacement or an
    elimination of a `Parte`) is removed. → invariant test on voci-dell-incontro
  - [INV-8] *(scope)* `Revisione` conserves the set of `Segmento`s of the whole `Incontro` (ids, intervals,
    text, and the `Parte` each belongs to); only their `Voce` and `confermato` flag change. A `Segmento` never
    changes `Parte`. → invariant test on voci-dell-incontro
  - [INV-I4] *(replaces [INV-12] and ADR 0018's per-generation scope)* `Voce n` is unique in the `Incontro`
    and a number, once given, is **never given again** in that `Incontro` — not after `unire`, not after a
    `Ritrascrivere` of any `Parte`, not after the elimination of every transcribed `Parte`, not when no `Voce`
    is left [user D-0007]. The label number is fixed at creation and never renumbered. Every new `Voce`
    (diarization of a `Parte`, `dividere`, `riassegnare` to a new `Voce`) takes the counter and increments
    it. → invariant test on voci-dell-incontro + migration test (counter = today's `prossima_voce`)
  - [INV-I16] *(NEW, amends ADR 0018 "the counters restart")* a `segmentoId` is never reused within its
    `Registrazione`, across generations: a replacement numbers the new `Segmento`s after every id the
    `Parte` ever used. [mod] *Why: a `superato` `Riassunto` keeps `Fonte`s `(registrazioneId, segmentoId)`;
    with reused ids a `Fonte` of the old generation would resolve to a new, unrelated `Segmento` — another
    minute, possibly another person — which D-0007 forbids for `Voce`s. It also makes the `superato`
    comparison exact without a separate generation number ([INV-I11]).*
    → invariant test on voci-dell-incontro
  - [INV-I5] The completion of an `Elaborazione` of ONE `Parte` changes only that `Parte`'s `Segmento`s,
    atomically, in the completion transaction (ADR 0018 §2 unchanged otherwise):
    - **first transcription:** the `Parte`'s `Segmento`s are added; its diarization clusters become NEW
      `Voce`s numbered from the counter by first appearance within the `Parte` (R24 rule, restricted to it);
    - **`Ritrascrivere`:** the `Parte`'s old `Segmento`s leave their `Voce`s; a `Voce` left empty is removed;
      a `Voce` with `Segmento`s in other `Parte`s keeps its identity; then the new `Segmento`s form NEW
      `Voce`s as above;
    - new `Voce`s are **never** joined automatically to existing ones (option (b) of `voci-tra-parti` is not
      modeled; the join is always `unire`);
    - a run that fails or is annullata changes nothing (ADR 0018 §6 unchanged).
    → invariant tests on voci-dell-incontro (`completaParte`) + AC tests on esegui-elaborazione
  - [INV-I6] On `RegistrazioneEliminata` of a `Parte` (synchronous, ADR 0020 subscriber amended): the veto on
    an open `Elaborazione` of THAT `Parte` is unchanged. Otherwise its `Elaborazione`s go, and:
    - a non-last `Parte`: its `Trascritto` leaves the root (`rimuoviParte`): its `Segmento`s leave their
      `Voce`s, emptied `Voce`s are removed, other `Voce`s keep their identity and number;
    - the last `Parte` (the `Incontro` ceases): the whole root goes.
    → eliminazione-registrazione-policy block (amended)
  - [INV-I7] A `Revisione` relates only `Voce`s and `Segmento`s of ONE `Incontro`; the `Parte`s may differ,
    the `Incontro`s never. Example: `unire` Voce 2 (part 1) with Voce 5 (part 2) [map].
    → invariant test on voci-dell-incontro (a `VoceRef`/`Segmento` of another `Incontro` → not found)
- **Domain events:**
  - `ElaborazioneAvviata` / `ElaborazioneFallita` / `ElaborazioneAnnullata` — unchanged (per `Registrazione`).
  - `ElaborazioneCompletata` (registrazioneId) *(+ `incontroId`)* → read-models (list, `Incontro` page,
    Trascritto view) + Sbobinatura `Rigenerazione` of THAT `Parte` + Parlanti `Proposta` / cross-Parte
    proposal cache refresh (after commit) + Sintesi view refresh (`superato` derived).
  - `TrascrittoSostituito` (registrazioneId) *(amended: + `incontroId` + the `Voce`s removed by the
    replacement)*, published before `ElaborazioneCompletata`, only on a replacement → Parlanti policy
    (synchronous, [INV-I8b]) + `AggiornamentiVista`. **Sintesi no longer subscribes** (D-0004, D-0007:
    `superato` is derived) — **for the architect** (ADR 0018 §5, ADR 0021 §6).
  - Voce-level consequence of eliminating a `Parte` (NEW; name for the architect to pin, e.g. a
    "Trascritto eliminato" event: `registrazioneId`, `incontroId`, the `Voce`s removed), published by the
    Trascrizione elimination policy inside the deleting transaction, only when that `Parte` had a
    `Trascritto` → the same Parlanti policy (synchronous) + `AggiornamentiVista`. [mod]
  - `VociUnite` / `VoceDivisa` / `SegmentoRiassegnato` / `SegmentoConfermato` *(keyed by `incontroId`;
    `Segmento`s as `(registrazioneId, segmentoId)`)* → Parlanti revisione-policy [INV-21] + Sbobinatura
    `Rigenerazione` fan-out (every `Parte` where an affected `Voce` has or had `Segmento`s) + Trascritto view +
    Sintesi view refresh.
- **Commands (+ actor):**
  - `AvviaElaborazione` *(amended)* — per `Registrazione` for "Riprova" / "Ritrascrivi" (unchanged), and
    NEW at `Incontro` level (actor: utente — "Trascrivi" on the `Incontro`): ONE `NumeroPersone` (optional,
    1..10) for the whole `Incontro`, ONE transaction, one `Elaborazione` `in_attesa` for each `Parte` that has
    neither a `Trascritto` nor an open `Elaborazione`, **queued in `Parte` order** (so the earlier `Parte` gets
    the lower `Voce` numbers) [user 2026-10-01, spike evidence: the count must be asked once per `Incontro`
    and applied to each `Parte`]. The prefill of every later field of the `Incontro` ("Trascrivi" of a newly
    imported `Parte`, "Riprova", "Ritrascrivi") is the latest `NumeroPersone` used in the `Incontro`, derived
    from its `Elaborazione`s, never stored apart. A `Parte` with fewer people is corrected by `unire`, or by
    "Ritrascrivi" of that `Parte` with its own value. [mod on the prefill and the order]
    → avvia-elaborazione application-service block
  - `UnisciVoci`, `DividiVoce`, `RiassegnaSegmento`, `RiassegnaSegmenti`, `ConfermaSegmento` — unchanged
    semantics, addressed by `incontroId` ([INV-I7]).
- **Presentation rule (not a domain guard), for UX:** while a `Ritrascrivere` of ANY `Parte` of an `Incontro`
  is queued or running, editing (Revisione, naming) of that `Incontro` is **read-only** (ADR 0018 (b) §2
  widened from the `Registrazione` to the `Incontro`); a FIRST transcription of a newly imported `Parte` does
  NOT make it read-only, because it only adds `Voce`s. [mod, the seed's lean] *Note: with [INV-I4] and
  [INV-I16] a stale command can no longer hit a new `Voce` or `Segmento` with a reused number, so this rule is
  no longer needed for safety; it avoids wasted edits on a `Parte` about to be replaced. UX may relax it.*
- **Policy:** startup `RecuperaElaborazioniInterrotte` unchanged.

## Tactical model — Parlanti — every row names the consumer, or it is not written
- **Aggregates / entities:**
  - `Parlante` (root) — unchanged, except its `ImprontaVocale`s are keyed per `(VoceRef, Parte)` ([INV-I8]).
  - `Attribuzione` (root, one per `VoceRef = (incontroId, voceId)`) [user D-0002]. One `Attribuzione` names
    the person in every `Parte` of the `Incontro`.
  - `Galleria`, `Proposta`, `Candidato`, `Fascia`, `EstrattoAudio`, `Proposta di unione`, `PianoRiassegnazione`
    stay computed, never stored.
- **Invariants:**
  - [INV-I8] *(amends [INV-14], [INV-15]; derived from ADR 0020 [INV-28] and ADR 0009, the architect
    confirms)* an `ImprontaVocale` is sourced from the `Segmento`s of ONE attributed `Voce` in ONE `Parte`
    and records that `Parte`. A `Parlante` holds at most ONE per (`VoceRef`, `Parte`). `ConfermaAttribuzione`
    and `SaltaVoce` extract one per `Parte` where the `Voce` has a non-empty `SorgenteImpronta` (same
    extract-then-transaction shape; `VoceCambiata` if any per-`Parte` source changed). A print may exist only
    while `Attribuzione(v) = P` and P is `attivo` (INV-15's "only if" half unchanged); a print is NOT required
    for every `Parte` of the `Voce` (e.g. a `Parte` joined later by `unire`). [mod on "not required"]
    → invariant test on parlante + conferma-attribuzione / salta-voce blocks
  - [INV-I8b] *(amends ADR 0018 §3 / ADR 0020 purge)* on `TrascrittoSostituito` or on the elimination
    consequence of a `Parte` (synchronous): every `ImprontaVocale` sourced from THAT `Parte` is removed,
    whichever `Voce` and `Parlante` it belongs to; the `Attribuzione`s of the `Voce`s REMOVED are dropped;
    a `Voce` that survives keeps its `Attribuzione` [map]; then [INV-25]. Every purge by `Registrazione` is
    exact because of [INV-I8]. → sostituzione-trascritto-policy block (amended)
  - [INV-17] *(scope)* an `Attribuzione` targets an `attivo` `Parlante` of the same `Progetto` as the
    `Incontro`, and an existing `Voce` of the `Incontro`.
  - [INV-19] *(amended)* "Ospite del <data>" uses the date of the `Incontro` = the `DataRegistrazione` of its
    first `Parte` ([INV-I2]) at the moment of `SaltaVoce`; stored, never follows later changes (Q-5
    unchanged). → salta-voce block (reads the `Incontro`'s `Parte`s through its port)
  - [INV-20] *(scope)* the `Proposta` for a `Voce` of an `Incontro`: the `Galleria` includes the prints from
    the OTHER `Parte`s of the same `Incontro` [map]; the `Voce`'s transient embeddings are one per `Parte` it
    has `Segmento`s in, and a `Candidato`'s `Fascia` is the BEST over (`Voce` slice, print) pairs. Ranking
    unchanged. → proposta read-model block
  - [INV-21] *(scope + one addition)* keyed by `Incontro` `VoceRef`; unchanged, plus: in `unire(A, B)` with A
    and B on the SAME `Parlante`, B's prints for `Parte`s where A has none are re-keyed onto A (stale,
    refreshed after commit); for a `Parte` where both have one, A's is kept and B's dropped. The inheritance
    exception (A unattributed, B attributed) re-keys B's prints keeping their `Parte`. [mod]
    → revisione-policy block
  - [INV-22] *(scope)* two `Voce`s of the SAME `Incontro` (same or different `Parte`s) attributed to the same
    `Parlante` yield a `Proposta di unione` — the name-driven cross-`Parte` join, independent of
    `voci-tra-parti` [map]. → proposta-di-unione read-model block
  - [INV-27] *(scope)* `PianoRiassegnazione` and the frasi di riferimento are computed over the whole
    `Incontro`: a reference `Parlante`'s target `Voce` is its lowest `voceId` in the `Incontro`; "≥ 1
    `Segmento` left" is counted in the `Incontro`. [mod: `Incontro` scope kept, because the spike evidence
    shows FORTE 4/4 across two files; revisit to per-`Parte` only if `voci-tra-parti` closes negatively]
    → piano-riassegnazione read-model block
  - [INV-I17] **`EstrattoAudio` is taken from ONE `Parte`** [mod]: for a `Voce`, from its `Segmento`s in the
    `Parte` where it has the most speech (tie: the earlier `Parte`, [INV-I2]), with the unchanged selection
    function (ADR 0012 (b), same as `SorgenteImpronta`); for a `Candidato`, from the `Parte` that sourced the
    chosen print. So it is always one file and one player load (ADR 0005 unchanged).
    → estratto-audio read-model block
  - [INV-I18] *(working hypothesis, pending spike `voci-tra-parti`, option (a))* the print-based cross-`Parte`
    proposal pairs `Voce` A with `Voce` B only if: both are of the same `Incontro`; both are **unattributed**;
    they share NO `Parte`; B is A's ONLY FORTE among the eligible `Voce`s and A is B's only FORTE (mutual,
    1:1); neither is already in another proposed pair. Anything else (two FORTE, a FORTE already taken, only
    DEBOLE) proposes nothing: the user joins by hand (`unire`). It writes nothing, keeps no embedding,
    exposes no number; confirming it is ONE gesture that sends `UnisciVoci` with the `Voce` of the earlier
    `Parte` surviving. Never automatic. [user 2026-10-01]
    → read-model block **blocked by spike `voci-tra-parti`** (the ONLY block it blocks)
- **Domain events:** unchanged names, keyed by `Incontro` `VoceRef`: `AttribuzioneConfermata` → Sbobinatura
  `Rigenerazione` of every `Parte` where the `Voce` has `Segmento`s + identification read-model + `Proposta di
  unione` + cross-`Parte` proposal refresh; `ImpronteRiallineate` *(keyed by `incontroId`)* → `Proposta`
  cache invalidation.
- **Commands (+ actor):** unchanged, addressed by `Incontro` `VoceRef`; `RiallineaImpronte(incontroId)`
  *(was registrazioneId)*.
- **Policies:** on `VociUnite` / `VoceDivisa` / `SegmentoRiassegnato` → [INV-21] (unchanged path); on
  `TrascrittoSostituito` and the `Parte`-elimination consequence → [INV-I8b].

## Tactical model — Sbobinatura — every row names the consumer, or it is not written
- **Aggregates / entities:** none — still one pure projection per `Registrazione` (`Parte`) [map].
- **Invariants:**
  - [INV-23] unchanged (deterministic, never read back).
  - [INV-24] *(scope)* each `Voce` renders as the `Nome` of its `Incontro` `Attribuzione`, else "Voce n" with
    the `Incontro` number (the part-2 `Sbobinatura` may show "Voce 4"). A 1-part `Incontro` renders as today.
    → view test on the sbobinatura read-model block
- **Policy:** `Rigenerazione` fan-out: a `Revisione` or an `Attribuzione`/`Nome` change on a `Voce`
  regenerates the `Sbobinatura` of EVERY `Parte` where that `Voce` has (or, for `unire`/`riassegnare`, had)
  `Segmento`s; `ElaborazioneCompletata` and `DataRegistrazioneModificata` regenerate only their `Parte`;
  importing a `Parte` or editing `OraDiInizio` changes no `Sbobinatura`. → rigenerazione side-effect block

## Tactical model — Sintesi — every row names the consumer, or it is not written
*(modeled in full: `contesto-lungo` closed by D-0010 — one pass over the concatenated `Parte`s in order)*
- **Aggregates / entities:**
  - `Riassunto` (root) — **re-keyed from `registrazioneId` to `incontroId`** [user D-0001]. Guards what it
    guards today, plus: its `Fonte`s are `(registrazioneId, segmentoId)`; its recorded structure is the
    structure of the `Incontro` read for the run ([INV-I11]). Speakers stay `Voce` references (now `Incontro`
    numbers), no `Nome`, no `ParlanteId` ([INV-S5] unchanged); the run's input legend is always `Voce n`
    (ADR 0032 unchanged).
    → `aggregate` block (riassunto, amended) + architect (`7.sqm`: re-key, `Fonte` rows gain
    `registrazione_id`, structure encoding, partial unique indexes per `Incontro`)
  - Lunghezza massima del Riassunto — unchanged.
- **Invariants:**
  - [INV-S2], [INV-S3] *(scope)* per `Incontro`. → riassumi / esegui-riassunto blocks + indexes
  - [INV-I9] *(amends [INV-S6]; the analyst's default, the user may revisit it)* "Riassumi" on an `Incontro`
    is accepted only if, read in its transaction: the LLM model is installed; **every** `Parte` has a
    `Trascritto`; **no** `Elaborazione` of any `Parte` is open; [INV-S2] holds; the concatenated input of all
    `Parte`s in order fits `LimiteIngresso` (ADR 0026, unchanged constants); the `Argomento` is within its
    bound. The too-long refusal keeps its meaning, now on the `Incontro`'s input (renaming the error is the
    architect's). → riassumi block (fake ports)
  - [INV-I19] *(the input, D-0010)* the input is ONE pass: every `Parte`'s `Segmento`s, `Parte` after `Parte`
    in [INV-I2] order, each `Segmento` in [INV-7] order within its `Parte`. Each `Segmento` carries a label
    unique within the `Incontro` that maps back to exactly one `(registrazioneId, segmentoId)`, and its `Voce`
    as `V<n>` (`Incontro` number). The label syntax and any `Parte` separator are the architect's (ADR 0021 §4
    input format). No split, no recompose.
    → `IngressoRiassunto` (sintesi domain, table test) + esegui-riassunto
  - [INV-I10] *(amends [INV-S4])* `Verifica delle fonti` per `Parte`: each `Fonte` must name a `Parte` of the
    `Incontro` as read for the run and a `segmentoId` that exists in THAT `Parte`'s `Trascritto` as read; a
    bound speaker (`Responsabile`, a `PuntoChiave`'s speaker, a text token) must be a `Voce` of the `Incontro`
    as read; a `PuntoChiave`'s speaker must be among the `Voce`s of its valid `Fonte`s, which may lie in
    different `Parte`s. Drop and count rules unchanged. → table tests on the riassunto block
  - [INV-I11] *(replaces [INV-S7]'s basis)* `superato` is **derived, never stored**: a `pronto` `Riassunto`
    is `superato` iff the current structure of its `Incontro` differs from the one it recorded. The structure
    = the ordered list of `Parte`s (by `registrazioneId`) and, for each, its set of
    `(segmentoId → voceId)`. It therefore catches: a `Revisione` (across `Parte`s included); every completed
    (re)transcription of any `Parte`, 1-part included [user D-0007] — the new `Segmento`s have new ids
    ([INV-I16]) and new `Voce` numbers ([INV-I4]), so no explicit generation number is needed [mod]; a reorder
    by a date/time edit [D-0009]; an eliminated `Parte` [D-0003]; an imported `Parte` [D-0008]. Names,
    `Attribuzione`s, promotion, `Eliminazione del Parlante`, `ConfermaSegmento` do NOT make it `superato`
    (unchanged). A change that restores the exact structure clears it. Never regenerated automatically
    (D-0004, D-0007). → pure predicate on the riassunto block + view test on riassunto-vista
  - [INV-I12] *(user-confirmed lean (a))* membership or order may change while a `Riassunto` of the
    `Incontro` is `in_attesa` / `in_corso`:
    - a queued one reads the `Incontro` when it STARTS; a `Parte` that has no `Trascritto` at that moment is
      left out and the `Riassunto` is born `superato` (its structure lacks that `Parte`) [mod]; if no `Parte`
      has a `Trascritto`, it ends `fallito` with the existing "no verifiable content" reason [mod];
    - a running one completes **born `superato`** (as [INV-S7] for a `Revisione` during the run);
    - an `Incontro` that ceased to exist during the run gets nothing written (compare-and-set, [INV-S8]).
    → esegui-riassunto block (race cases)
  - [INV-I12b] *(amends [INV-S8]; conflicts with ADR 0020 [INV-28], ADR 0024, ADR 0021 §6 — for the
    architect)*:
    - `TrascrittoSostituito` of any `Parte` (1-part included): **nothing is deleted, nothing re-enqueued**; the
      `Riassunto` becomes `superato` by derivation [user D-0004, D-0007]. Sintesi has no subscriber for it;
    - `RegistrazioneEliminata` of the LAST `Parte` (the `Incontro` ceases): every `Riassunto` of the `Incontro`
      is deleted, in any state, in the deleting transaction, never vetoing (ADR 0024 behaviour, per
      `Incontro`);
    - `RegistrazioneEliminata` of a non-last `Parte`: nothing is deleted; `superato` by derivation; its
      `Fonte`s naming the eliminated `Parte` survive [user D-0003, privacy cost accepted].
    → eliminazione-registrazione-sintesi-policy block (amended); the sostituzione-trascritto-sintesi-policy
    block is REMOVED
  - [INV-I13] *(extended to `Fonte`s)* a reference to a `Voce` that no longer exists in the `Incontro`
    (`Responsabile`, `PuntoChiave` speaker, text token, a `Fonte`'s speaker) renders as "a `Voce` no longer
    present", never as a `Nome` nor as a live "Voce n" [user D-0007]; a `Fonte` whose `Segmento` no longer
    exists (its `Parte` re-transcribed or eliminated) renders as a `Fonte` no longer present, never resolved
    to another `Segmento` ([INV-I16]) [mod]. The names port must tell "no longer present" from "unattributed".
    Wording: UX. → view test on riassunto-vista
  - [INV-S10] unchanged, minus the automatic re-summary (removed).
- **Domain events** (after commit, keyed by `incontroId`): `RiassuntoRichiesto`, `RiassuntoAvviato`,
  `RiassuntoPronto`, `RiassuntoFallito`, `RiassuntoEliminato` → `riassunto-vista` + the `:avvio` queue signal
  + best-effort cancel (unchanged roles).
- **Read-models:**
  - `riassunto-vista` per `Incontro` (consumer: the Riassunto tab of the `Incontro` page): as today, plus each
    `Fonte` as (speaker, "parte n · mm:ss"; minute only for a 1-part `Incontro`, [INV-I3]), `superato`
    ([INV-I11]), the "no longer present" renderings ([INV-I13]), why "Riassumi" is unavailable per [INV-I9]
    (which `Parte` lacks a `Trascritto` / has an open run).
  - `RiassuntiInAttesa` → items `(riassuntoId, incontroId, richiestoAlle)` for the shared queue (ADR 0023).
- **Commands (+ actor):**
  - `Riassumi` (incontroId, argomento?) (actor: utente — "Riassumi" / "Riassumi di nuovo") → riassumi block.
  - `EseguiProssimoRiassunto` (actor: sistema) → esegui-riassunto block: reads the `Incontro`'s ordered
    `Parte`s (Progetto port), the `Segmento`s of each (Trascrizione port), builds the one-pass input
    ([INV-I19]), calls the LLM outside any transaction, applies [INV-I10], completes by compare-and-set.
  - `RecuperaRiassuntiInterrotti` — unchanged.
  - **There is no automatic "Riassumi" any more**, 1-part included [user D-0004, D-0007].
- **Ports (consumer-owned, in-process):** `LettoreTrascritto` of Sintesi reads per `Parte` (unchanged shape)
  and "any `Elaborazione` open" per `Parte`; `LettoreNomi` by `Incontro` `VoceRef`, telling "attributed",
  "unattributed" and "no longer present" apart; NEW: Sintesi's own reader of an `Incontro`'s ordered `Parte`s
  (Progetto → Sintesi, map). Shapes: architect.
- **Build note (for build-manifest, D-0010):** the 3 h time check that `contesto-lungo` did not run becomes a
  `tests_nl` of the `Incontro` `Riassunto` block (esegui-riassunto or the real `ModelloLinguistico` adapter,
  `@Tag("modelli")`, opt-in): a real 2–3 `Parte` `Incontro` of ≈ 3 h summarized in ≤ 10 min per hour of audio
  (ADR 0026), with the user judging that no `Decisione` spanning two `Parte`s appears twice.

## Migration (`7.sqm`, forward-only, ADR 0006) — facts the architect must keep
- One `Incontro` per existing `Registrazione` (same `Progetto`); `registrazione.incontro_id` set to it;
  `OraDiInizio` EMPTY for every existing row (no file is read by a migration; the spike's rule applies only to
  new imports) [mod].
- The Voci dell'Incontro root of each migrated `Incontro` takes the `Voce` counter of its `Trascritto`
  (`prossima_voce`); `Voce` numbers are unchanged ([INV-I3]).
- `Attribuzione` and `ImprontaVocale` re-keyed 1:1 to `(incontroId, voceId)`; every existing print records
  its `Registrazione` as its `Parte` ([INV-I8]).
- Every `Riassunto` passes to its 1-part `Incontro`; its `Fonte`s gain the `registrazioneId`; its recorded
  structure is re-expressed in the new encoding so that an unchanged `Trascritto` compares EQUAL — an existing
  `pronto` `Riassunto` must not become `superato` by the migration ([INV-I3]).
- Migration test: a pre-feature DB migrated, then every view of a 1-part `Incontro` equals today's.

## Conflicts with accepted ADRs — for the architect (not decided here)
1. **ADR 0018** — [INV-12] per-generation reuse ("renumbered from 1", "the counters restart") is replaced by
   [INV-I4] (Voce numbers never reused per `Incontro`) and [INV-I16] (`segmentoId`s never reused per
   `Registrazione`); its rejected option "monotonic numbering" is now the rule. §3 purge "every `VoceRef` of
   the `Registrazione`" becomes [INV-I8b] (only removed `Voce`s + prints of that `Parte`). §5: Sintesi is no
   longer a consumer of `TrascrittoSostituito`; the payload gains `incontroId` + removed `Voce`s. Amendment
   (b) §2 read-only widened to the `Incontro` (presentation). The Trascritto is no longer a root.
2. **ADR 0020** — [INV-28] "no row keyed by its `registrazioneId` survives": false for a non-last `Parte`
   (`Fonte`s in a `superato` `Riassunto`, D-0003); `Attribuzione`s are no longer keyed by `registrazioneId`;
   §2 Parlanti purge moves behind a Trascrizione Voce-level event; the `Incontro` row goes with the last
   `Parte`; `RegistrazioneEliminata` gains `incontroId` + "`Incontro` ceased"; §6 dialog text must differ for a
   non-last `Parte` (the `Riassunto` is kept, `superato`).
3. **ADR 0021 §6** — the sostituzione-trascritto policy (delete + auto re-enqueue) is removed; §3 port rows
   re-keyed by `Incontro`, plus a new Progetto → Sintesi reader; §4 input format needs a per-`Incontro` unique
   `Segmento` label ([INV-I19]).
4. **ADR 0022** — `riassunto.registrazione_id` → `incontro_id` (the IMMEDIATE FK now to `incontro`, which
   keeps the fail-closed guarantee only for the last `Parte`); both partial unique indexes per `incontro_id`;
   `riassunto_fonte` gains `registrazione_id` (no FK); `struttura` encoding qualified by `Parte` and order;
   the ADR 0022 `enforced_by` checks must be updated.
5. **ADR 0023** — queue items and `PosizioniNellaCoda.riassunti` keyed by `incontroId`; the `Elaborazione`s
   of one `Incontro`-level "Trascrivi" must be served in `Parte` order (same-instant tie by random id is not
   enough).
6. **ADR 0024** — the Sintesi eliminazione policy deletes only when the `Incontro` ceases; [INV-28]'s
   "every `Riassunto` of it" and the dialog text change accordingly.
7. **ADR 0009 / ADR 0012 (b)** — prints per (`Parlante`, `VoceRef`, `Parte`); `RiallineaImpronte` keyed by
   `incontroId`; whether it may ever INSERT a print for a `Parte` slice gained by `unire`/`riassegnare` (the
   model does not require it, [INV-I8]).
8. **ADR 0019** — [INV-27] scope widened to the `Incontro`.
9. **ADR 0030** — declared subscriber order Sintesi → Parlanti → Trascrizione: the Parlanti purge now runs
   from the event Trascrizione publishes inside its own subscriber (nested), after it.
10. **ADR 0014** — one `NumeroPersone` applied to several `Parte`s by one gesture; prefill from the latest
    run of the `Incontro`.
11. **ADR 0007** — the `riassunto` indexes re-keyed; a `voce` primary key per `incontro_id`.
