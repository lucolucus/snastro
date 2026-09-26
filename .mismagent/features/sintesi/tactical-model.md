# Tactical model — sintesi

> Strategic map (contexts, ubiquitous language, relationships): `.mismagent/context-map.md`
> (bounded context `Sintesi`, added 2026-09-25). The canonical names come from THERE — this file
> never renames them. Model spike: `research/scelta-modello-llm.md`.
> Single side (`app`): every boundary below is **in-process** (port + contract test), no OpenAPI,
> no `operationId`.
> `[user]` = decided by the user on 2026-09-25 (analyst's ambiguities, folded into the map).
> `[mod]` = decided by the tactical-modeler on 2026-09-25 inside what the map leaves open;
> **all accepted by the user at the model checkpoint of 2026-09-25**.
> `[user-cp]` = the user's answers at that checkpoint (Q-S1 → "download only"; new per-`Progetto`
> "lunghezza massima del Riassunto").
> Numbering reserved for this feature: ADRs from **0021**, forward-only migration **`6.sqm`**
> (`5.sqm` is ADR 0020's `eliminazione_in_sospeso`). Invariant ids `[INV-S…]` are local to this
> file (prefix `S` so they never collide with trascrizione-con-parlanti's INV-1…INV-28).

## Seeds for the tactical (to be consumed — the analyst writes, the tactical-modeler absorbs)
<!-- Absorbed by mismagent-tactical-modeler on 2026-09-25 into the sections below. Empty. -->

## Seam granularity (what crosses a context boundary — modeled decisions)
- **`Segmento` (Trascrizione → Sintesi):** crosses as ONE unit keyed by `(registrazioneId,
  segmentoId)`, carrying `voceId`, `inizio`, `fine` (ms) and the text — the same shape as
  `Documento`'s `SegmentoVista`, in time order ([INV-7] of trascrizione-con-parlanti). Sintesi
  never re-models the `Trascritto`: it holds no `Voce`/`Segmento` entity of its own.
- **`Fonte`:** ONE `segmentoId` (a unit, never a range or a list of ids inside one `Fonte`), scoped
  by the `registrazioneId` of its `Riassunto`. The minute shown is derived at display from the
  `Segmento`'s `inizio`; the speaker from its `voceId` → current `Nome`. An element holds a SET of
  `Fonte`s (no duplicate id inside one element).
- **`Voce` (Trascrizione/Parlanti → Sintesi):** crosses as `VoceRef = (registrazioneId, voceId)`;
  inside a `Riassunto` only the `voceId` is stored (the `registrazioneId` is the `Riassunto`'s).
  `Responsabile`, a `PuntoChiave`'s speaker and every speaker token in text are `voceId`s. No
  `Nome`, no `ParlanteId` ever crosses INTO storage; `Nome` crosses only at read time through the
  names port (`VoceRef` → `Nome`, "Voce n" while unattributed).
- **Trascrizione state (→ Sintesi):** two booleans per `Registrazione` — "a `Trascritto` exists",
  "an `Elaborazione` is `in_attesa | in_corso`" — read through Sintesi's own port.
- **`Registrazione` (Progetto → Sintesi):** only its `registrazioneId`, and only through the
  published `RegistrazioneEliminata`. Sintesi reads nothing else from Progetto (no port). The
  lunghezza massima del Riassunto is keyed by `progettoId` but owned by Sintesi: nothing crosses.
- **`Riassunto`** crosses no seam: it is read only by the UI (Riassunto tab) through a read-model.
- No quantity enters a conserved invariant. The only count is the number of elements dropped by the
  `Verifica delle fonti` (an attribute of the `Riassunto`, shown as "n omessi").

## Tactical model — Sintesi — every row names the consumer, or it is not written
- **Aggregates / entities:**
  - `Riassunto` (root, one per request; identity `riassuntoId`; references `registrazioneId`)
    guards: `StatoRiassunto`, `Argomento` (optional, immutable), the lunghezza massima del
    Riassunto it was requested with (immutable, [INV-S10]), the request instant (FIFO key of
    the shared queue), the failure reason (when `fallito`), and — only once `pronto` — the
    `Sommario`, the lists of `Decisione` / `QuestioneAperta` / `Azione` / `PuntoChiave`, the count
    of elements dropped by the `Verifica delle fonti`, and **the structure of the `Trascritto` it
    was made from** (the `segmentoId → voceId` assignment of the one read the LLM input was built
    from; basis of `superato`, [INV-S7]).
    → manifest `aggregate` block (riassunto) + architect decision (persistence in the project DB
    through a `:persistenza` adapter + `6.sqm`; `Sommario` and every element's text as plain
    queryable TEXT columns, one row per element and per `Fonte` — never a JSON blob — so a later
    FTS5 index is not precluded; how the structure is kept: full `segmentoId → voceId` rows vs a
    digest — architect)
  - `Decisione`, `QuestioneAperta`, `Azione`, `PuntoChiave` — value objects inside the root, each
    with text (speakers as `voceId` tokens) and a non-empty set of `Fonte`s; `Azione` + optional
    `Responsabile` (`voceId`); `PuntoChiave` + optional speaker (`voceId`). `Sommario` = VO (prose,
    speakers as `voceId` tokens). `Argomento` = VO (trimmed; blank ⇒ absent; bounded length).
    → part of the riassunto `aggregate` block (VO table tests)
  - **Lunghezza massima del Riassunto** [user-cp] — the per-`Progetto` cap, in words, on the length
    of a `Riassunto` the LLM is asked to write (default **2000 parole**). It is a **Sintesi**
    setting (only Sintesi reads it; `Progetto` does not know it), keyed by `progettoId`, stored in
    the `Progetto`'s database. Modeled as a small root (identity = `progettoId`) holding one VO
    (a word count). **No row ⇒ the default** (nothing to backfill; a `Progetto` created before
    this feature reads 2000).
    → manifest `aggregate` block (lunghezza-massima-riassunto) + architect decision: a Sintesi-owned
    table in `6.sqm` (e.g. one row per `progettoId`, no FK needed since the `Progetto` DB is
    per-project) vs a column — flagged to the architect.
    *Language gap (analyst):* the term is the user's; it is not yet in `context-map.md` — the
    analyst adds it to Sintesi's ubiquitous language (Not: limite parole, max token, lunghezza
    output).
- **Invariants:**
  - [INV-S1] `StatoRiassunto` moves only `in_attesa → in_corso → pronto | fallito`; `pronto` and
    `fallito` are terminal (mirror of Trascrizione INV-3). Content (`Sommario`, elements, structure,
    omitted count) exists iff `pronto`; a failure reason exists iff `fallito`.
    → invariant-test on the riassunto aggregate block
  - [INV-S2] per `Registrazione`, at most ONE `Riassunto` is `in_attesa | in_corso`; a "Riassumi"
    while one is open is refused (`RiassuntoGiaAperto`). No `AnnullaRiassunto` exists [user].
    → set rule: test on the riassumi application-service block + partial unique index (ADR 0007
    pattern, `6.sqm`)
  - [INV-S3] **only the current `Riassunto` is kept** [user]. Per `Registrazione`, at most ONE
    `pronto` (the shown one) and at most ONE other (`in_attesa | in_corso | fallito`). A transition
    to `pronto` deletes the previous `pronto` **in the same transaction** (replaced whole); a
    transition to `fallito` leaves the shown `pronto` byte-for-byte unchanged; a new "Riassumi"
    deletes a previous `fallito` in its transaction [mod].
    → test on the esegui-riassunto and riassumi application-service blocks + partial unique index
    on `pronto` (`6.sqm`)
  - [INV-S4] **`Verifica delle fonti`** — a `Riassunto` becomes `pronto` only through the check,
    applied by the root to the raw LLM answer against the structure of the `Trascritto` read for
    that run:
    - every `Fonte` is a `segmentoId` of that `Trascritto`; an invalid one is dropped; duplicate ids
      inside one element are collapsed;
    - every `Decisione` / `QuestioneAperta` / `Azione` / `PuntoChiave` keeps ≥ 1 valid `Fonte`,
      otherwise it is dropped and counted;
    - a `Responsabile` is a `voceId` of that `Trascritto` or none; a `PuntoChiave`'s speaker is one
      of the `voceId`s of its (valid) `Fonte`s. An invalid binding is **removed** (the `Azione`
      keeps no `Responsabile`, the `PuntoChiave` becomes unbound) and the element stays, since it
      still has its `Fonte`s [mod];
    - every speaker token in an element's text is a `voceId` of that `Trascritto`, otherwise the
      element is dropped and counted; a `Sommario` with an invalid token is dropped (shown empty)
      and counted [mod];
    - an answer left with no `Sommario` AND no element is NOT `pronto`: the run ends `fallito`
      ("nessun contenuto verificabile"), so the shown `Riassunto` is kept ([INV-S3]) [mod];
    - nothing dropped is ever stored or shown; only the count is.
    → invariant-tests on the riassunto aggregate block (table test per rule)
  - [INV-S5] **speakers only as `Voce` references** [user]: a `Riassunto` stores no `Nome` and no
    `ParlanteId`, anywhere (`Responsabile`, `PuntoChiave` speaker, `Sommario`, element text). Names
    are resolved at display through the names port ("Voce n" while unattributed).
    → invariant-test on the riassunto aggregate block + mechanical `enforced_by` (architect: no
    Parlanti type imported by `:sintesi:dominio`/`:sintesi:applicazione` except through the port)
  - [INV-S6] `Riassumi` is accepted only if, read inside its transaction: the LLM model is
    installed (`ModelloNonInstallato`) [user-cp]; a `Trascritto` exists
    (`TrascrittoNonDisponibile`); no `Elaborazione` of that `Registrazione` is `in_attesa |
    in_corso` (`ElaborazioneGiaAperta`); [INV-S2] holds; the input fits the limit
    (`RegistrazioneTroppoLunga`; ≈ 1 h 15 until spike `contesto-lungo`); the `Argomento`, if given,
    is within its length bound (`ArgomentoTroppoLungo`; bound fixed by spike `filtro-fuori-tema`,
    provisional 200 characters) [mod]. The limit is measured on the labelled LLM input estimated
    from the `Trascritto` text (no model needed, so it can be stated before any download), with the
    runtime's exact token count as a backstop at run time → `fallito` "registrazione troppo lunga"
    [mod]. → tests on the riassumi application-service block (fake ports)
  - [INV-S7] **`superato` is derived, never stored**: a `pronto` `Riassunto` is `superato` iff the
    current `Trascritto`'s `segmentoId → voceId` assignment differs from the structure it was made
    from. Only a `Revisione` (unire, dividere, riassegnare, "Riassegna per somiglianza") can make it
    differ ([INV-8] of trascrizione-con-parlanti conserves ids and intervals); renaming, an
    `Attribuzione`, `Eliminazione del Parlante`, promotion and `ConfermaSegmento` cannot. A
    `Revisione` made while the run was `in_corso` makes it born `superato`. A `Revisione` that
    restores the exact assignment clears it (the references are valid again). A `superato`
    `Riassunto` is never regenerated automatically.
    → pure predicate on the riassunto aggregate block (invariant-test) + view test on the
    riassunto-vista read-model block
  - [INV-S8] **no `Riassunto` outlives its `Registrazione` or its `Trascritto` generation**: on
    `RegistrazioneEliminata` every `Riassunto` of it is deleted, and on `TrascrittoSostituito` every
    `Riassunto` of it is deleted — each **in the publishing transaction** (synchronous subscriber,
    ADR 0012). A run's completion (`in_corso → pronto | fallito`) is a **compare-and-set** on the
    row still existing and still `in_corso`; otherwise it writes nothing and publishes nothing (no
    resurrection). → tests on the eliminazione/sostituzione policy application-service blocks + the
    esegui-riassunto block (race cases)
  - [INV-S9] the lunghezza massima del Riassunto is an integer number of words within
    **[300, 2500]**, default 2000 [mod, provisional]; out of range → `LunghezzaMassimaFuoriIntervallo`,
    nothing written. The upper bound is technical, not a taste: the answer shares the 32k context
    with an input of up to ≈ 28k tokens (the unchanged input limit), and 2500 Italian words ≈
    3.5–4k tokens; it also keeps the output time inside the ≤ ~5 min budget (spike: 1 363 tokens
    out in 69 s). The bounds are re-fixed by spike `runtime-llm-in-app` (exact context/output
    budget) and `qualita-riassunto` (adherence). → VO table test on the lunghezza-massima-riassunto
    aggregate block + AC-test on its application-service
  - [INV-S10] the cap is fixed on a `Riassunto` when it is requested (the `Progetto`'s value at that
    moment, also for the automatic re-summary after `Ritrascrivere`) and passed to the prompt and
    the answer schema of that run. Changing the setting never touches a queued, running or `pronto`
    `Riassunto` and never makes it `superato`. The cap is a **request to the model, not a verified
    rule**: a `pronto` answer longer than the cap is kept whole — never truncated, never `fallito`
    (truncation would drop elements silently, against [INV-S4]'s "nothing dropped without being
    counted") [mod]; how well the model keeps to it is measured by spike `qualita-riassunto`.
    → invariant-test on the riassunto aggregate block + AC-test on riassumi / esegui-riassunto
- **Domain events** (all delivered after commit; `Documento` subscribes to none):
  - `RiassuntoRichiesto` (registrazioneId) → `read-model` riassunto-vista (tab shows "in coda") +
    `:avvio` shared queue signal (work available)
  - `RiassuntoAvviato` (registrazioneId) → `read-model` riassunto-vista ("in corso", elapsed time)
  - `RiassuntoPronto` (registrazioneId) → `read-model` riassunto-vista (new content replaces the old)
  - `RiassuntoFallito` (registrazioneId, motivo) → `read-model` riassunto-vista (inline reason, the
    shown `Riassunto` unchanged, `Argomento` prefilled)
  - `RiassuntoEliminato` (registrazioneId) → `read-model` riassunto-vista (tab emptied) + `:avvio`
    side-effect: if the running LLM job is for that `Registrazione`, cancel it best-effort (its
    completion would write nothing anyway, [INV-S8])
  - `LunghezzaMassimaRiassuntoModificata` (progettoId) → `read-model` impostazioni-sintesi (the
    setting's field shows the saved value). No other consumer: queued/`pronto` `Riassunto`s are
    unaffected ([INV-S10]).
- **Read-models:**
  - `impostazioni-sintesi` per `Progetto` → `read-model` block (consumer: the per-`Progetto`
    setting's field, place in the UI by the ux-designer): the current lunghezza massima del
    Riassunto (default when unset) + its bounds.
  - `riassunto-vista` per `Registrazione` → `read-model` block (consumer: the Riassunto tab of the
    recording page; supersedes trascrizione-con-parlanti AC-594 for that space). Carries: the shown
    `pronto` `Riassunto` with `voceId` tokens rendered as the current `Nome`; each `Fonte` as
    (speaker `Nome`, minute); `superato` ([INV-S7]); the omitted count; the open request's state
    (`in_attesa` + position in the SHARED queue, `in_corso` + elapsed); the LLM model's state
    (not installed / downloading + progress / download failed + reason / installed), read from
    `:modelli` and deciding what the tab's button does (Q-S1 below); the
    last `fallito` reason; the `Argomento` to prefill; whether "Riassumi" is available and, if not,
    why ([INV-S6], computed without side effects). Refreshed on the Sintesi events above and on the
    existing `Cambiamento(registrazioneId)` emitted for Trascrizione/Parlanti events (Revisione →
    `superato`; names → re-render).
- **Commands (+ actor):**
  - `Riassumi` (registrazioneId, argomento?) (actor: utente — "Riassumi" / "Riassumi di nuovo" in
    the Riassunto tab, only when the model is installed) → `application-service` block riassumi. One
    transaction: guards [INV-S6], read the `Progetto`'s lunghezza massima del Riassunto and fix it
    on the new `Riassunto` ([INV-S10]), delete a previous `fallito` ([INV-S3]), create `in_attesa`,
    publish `RiassuntoRichiesto`. It never starts a download (Q-S1).
  - **Model download** (actor: utente — the same tab button while the model is NOT installed,
    labelled as a download of the 6.6 GB model; also "Riprova" after a failed download)
    [user-cp, Q-S1 = (b)] → NOT a Sintesi command: the Riassunto tab's presenter invokes the existing
    `:modelli` provisioning for the optional LLM catalogue entry (architect; ADR 0008 amendment via
    spike `runtime-llm-in-app`). No `Riassunto` is created, the shared queue is untouched, a failed
    download changes nothing in Sintesi. When it completes, the user presses "Riassumi" again.
  - `ModificaLunghezzaMassimaRiassunto` (progettoId, parole) (actor: utente — a per-`Progetto`
    setting, UI place: ux-designer) [user-cp] → `application-service` block
    lunghezza-massima-riassunto; publishes `LunghezzaMassimaRiassuntoModificata` (progettoId)
  - `EseguiProssimoRiassunto` (actor: sistema — the `:avvio` shared FIFO queue, when a `Riassunto`
    is the oldest request among `Elaborazione`s and `Riassunto`s) → `application-service` block
    esegui-riassunto. Claim `in_attesa → in_corso` in one short transaction (reads the head inside
    it, like the Elaborazione claim) + `RiassuntoAvviato`; read the `Segmento`s and the current
    names through the ports and build the labelled input (`[s<segmentoId> V<voceId> m:ss]` + legend
    `V<n> = <Nome | Voce n>`) OUTSIDE any transaction; call the LLM port (schema-constrained, with
    the `Riassunto`'s lunghezza massima in the prompt/schema, [INV-S10]) outside any transaction; apply [INV-S4] on the root; commit `pronto` (replacing, [INV-S3]) or
    `fallito` with a compare-and-set ([INV-S8]). Failure reasons: model unavailable, runtime error,
    too long (exact count), no verifiable content, interrupted.
  - `RecuperaRiassuntiInterrotti` (actor: sistema — at project open, alongside
    `RecuperaElaborazioniInterrotte`) → same block (startup policy): a `Riassunto` found `in_corso`
    with no live run → `fallito` "interrotto"; `in_attesa` ones stay queued.
- **Policies:**
  - on `TrascrittoSostituito` (SYNCHRONOUS, in the completion transaction of the re-run) → delete
    every `Riassunto` of that `Registrazione` (any state) and, if at least one existed, create a new
    `in_attesa` `Riassunto` with the `Argomento` of the most recent one (actor: sistema; the ONLY
    automatic "Riassumi" [user]) — created in the same transaction so it becomes visible only when
    the new `Trascritto` commits, and a crash cannot lose it [mod]. It carries the `Progetto`'s
    current lunghezza massima del Riassunto ([INV-S10]). If the new `Trascritto` exceeds the limit,
    or the LLM model is no longer installed ([INV-S6]), nothing is created: the tab then shows the
    plain reason ("too long" / the model download button) like any other recording [mod, picks the
    seed's open detail; model case added with Q-S1]. A failed or annullata re-run
    publishes nothing, so nothing changes.
    → `application-service` block sostituzione-trascritto-sintesi-policy, reached through a
    synchronous subscriber adapter in `:sintesi:adattatori` (abbonato-trascrizione-sintesi)
  - on `RegistrazioneEliminata` (SYNCHRONOUS, in ADR 0020's deleting transaction) → delete every
    `Riassunto` of that `Registrazione`, `in_attesa` included (it will never run: the queue reads
    its head inside its claim transaction and no longer finds it). Never vetoes: an `in_corso`
    `Riassunto` does not block the deletion; its completion writes nothing ([INV-S8]). Extends ADR
    0020's [INV-28] list of what one elimination removes.
    → `application-service` block eliminazione-registrazione-sintesi-policy, reached through a
    synchronous subscriber adapter (abbonato-progetto-sintesi)
  - (technical, `:avvio`) ONE shared serial FIFO queue runs `Elaborazione`s and `Riassunto`s one at
    a time, strictly by request instant [user]; the LLM never runs inside a transaction (ADR 0012).
    → architect decision (queue owner computes the queue position shown by both S2's
    `stati-elaborazione` and `riassunto-vista`: Trascrizione must not read Sintesi rows)
- **Ports (consumer-owned, in Sintesi, in-process):**
  - `LettoreTrascritto` of Sintesi (`segmentoId`, `voceId`, `inizio`, `fine`, text, ordered; "a
    `Trascritto` exists"; "an `Elaborazione` is open") → `port` block + `adapter` block over
    Trascrizione's read side + contract test (same shape as Documento's)
  - `LettoreNomi` of Sintesi (`VoceRef` → `Nome`) → `port` + `adapter` over Parlanti + contract test
  - `ModelloLinguistico` (technical: labelled input + answer schema + lunghezza massima (words) →
    structured answer or error; cancellable) → `port` block with a fake (gate stays headless) + `adapter` block bound by spike
    `runtime-llm-in-app`
  - model availability (installed? downloading + progress? failed?) → consumer-owned read port over
    the `:modelli` provisioning, used by the [INV-S6] guard and by `riassunto-vista`; the download
    itself is triggered by the UI through `:modelli`, never by a Sintesi service (architect; ADR 0008
    amendment via spike `runtime-llm-in-app`)

## Tactical model — Trascrizione / Parlanti / Progetto / Documento
Unchanged by this feature: no new aggregate, invariant, command or event. Sintesi only subscribes to
the already-published `TrascrittoSostituito` (ADR 0018) and `RegistrazioneEliminata` (ADR 0020) and
reads through its own ports. Two consequences for their owners (architect):
- ADR 0020's [INV-28] and its dialog text gain "il riassunto" among what an elimination removes.
- S2's `posizioneInCoda` counts `Riassunto`s queued ahead (shared FIFO), computed by the `:avvio`
  queue owner, not by Trascrizione.

## Resolved at the model checkpoint (user, 2026-09-25)
- **Q-S1 → (b) "download only".** While the LLM model is not installed, the tab's button only
  starts its download through `:modelli`: NO `Riassunto` is created and the shared queue stays
  **strict FIFO** (no skip exception). When the download completes, the user presses "Riassumi"
  again. `Riassumi` itself refuses with `ModelloNonInstallato` ([INV-S6]); a model removed after a
  `Riassunto` was queued ends that run `fallito` "modello non disponibile".
- **The five `[mod]` choices** are accepted as written.
- **New: lunghezza massima del Riassunto** per `Progetto` (default 2000 parole, editable) →
  [INV-S9], [INV-S10], `ModificaLunghezzaMassimaRiassunto`. The `Argomento` bound (200 characters)
  and the input length limit stay as they are, and are NOT user parameters.
- **For the architect:** the `6.sqm` table/column for the setting; the `Riassunto`'s own column for
  the cap it was requested with; the cap in the answer schema/prompt of `ModelloLinguistico`.
