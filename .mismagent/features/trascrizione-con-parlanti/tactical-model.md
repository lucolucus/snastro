# Tactical model — trascrizione-con-parlanti

> Strategic map (contexts, ubiquitous language, relationships): `.mismagent/context-map.md`.
> The canonical names come from THERE — this file never renames them.
> Single side (`app`): every boundary below is **in-process** (port + contract test), no OpenAPI,
> no `operationId`.
> `[user]` = decided by the user on 2026-09-23 (seeds + the tactical-modeler's Q-1…Q-7, all
> answered with the recommended option). No open question remains.

## Seeds for the tactical (to be consumed — the analyst writes, the tactical-modeler absorbs)
<!-- Absorbed by mismagent-tactical-modeler on 2026-09-23 into the sections below. Empty. -->

## Seam granularity (what crosses a context boundary — modeled decisions)
- **`Registrazione` (Progetto → Trascrizione, Parlanti):** crosses as ONE unit keyed by
  `registrazioneId`, carrying `progettoId`, source audio reference, `DataRegistrazione`, duration.
  Trascrizione/Parlanti never write it.
- **`Voce` (Trascrizione → Parlanti, Documento):** crosses as ONE unit keyed by
  `VoceRef = (registrazioneId, voceId)`. `voceId` is stable for the life of the `Trascritto` and
  **never reused** (a `Voce` removed by `unire` does not free its id). The `n` of the label
  "Voce n" is fixed when the `Voce` is created (initial diarization or `dividere`/`riassegnare` to a
  new `Voce`) and never renumbered. `VoceRef` is the correlation key of `Attribuzione` and
  `ImprontaVocale`.
- **`Segmento` (Trascrizione → Parlanti, Documento):** crosses as ONE unit keyed by
  `(registrazioneId, segmentoId)`, carrying `voceId`, `inizio`, `fine` (ms from the start of the
  `Registrazione`) and — only towards `Documento` — the text. `Parlanti` reads intervals only, never
  the text.
- **`ImprontaVocale`:** exactly ONE per attributed `Voce` (unit, not aggregated), keyed by `VoceRef`,
  owned by the `Parlante` the `Voce` is attributed to. Never averaged.
- No quantity enters a conserved invariant in this feature; the only conservation is the
  **set of `Segmento`s** under `Revisione` ([INV-8]).

## Tactical model — Progetto — every row names the consumer, or it is not written
- **Aggregates / entities:**
  - `Progetto` (root) — identity + display name; scopes `Registrazione`s and `Parlante`s.
    → manifest `aggregate` block (progetto) + architect decision (persistence)
  - `Registrazione` (root, separate aggregate referencing `progettoId`) guards `DataRegistrazione`,
    duration, source audio reference.
    → manifest `aggregate` block (registrazione) + architect decision (source audio copied into the
    project vs referenced — infra-notes)
- **Invariants:**
  - [INV-1] a `Registrazione` belongs to exactly one `Progetto`; its `progettoId` is set at creation
    and immutable. → invariant-test on the registrazione aggregate block
  - [INV-2] `DataRegistrazione` is always set: defaults to the source file's date, replaceable only by
    a user-chosen date. → invariant-test on the registrazione aggregate block
- **Domain events:**
  - `ProgettoCreato` → `read-model` block: elenco dei Progetti (open/create screen)
  - `RegistrazioneAggiunta` → `read-model` block: Registrazioni del Progetto (with their
    `StatoElaborazione`, joined from Trascrizione) + policy auto-start (below) [user, Q-6]
  - `DataRegistrazioneModificata` → `read-model` Registrazioni del Progetto. It does NOT change any
    `Nome`: a provisional "Ospite del <DataRegistrazione>" is a stored value fixed at creation and
    does not follow a later date change (the user can rename it) [user, Q-5]
- **Commands (+ actor):**
  - `CreaProgetto` (actor: utente) → `application-service` block
  - `AggiungiRegistrazione` (actor: utente) → `application-service` block
  - `ModificaDataRegistrazione` (actor: utente) → `application-service` block
- **Policy:** on `RegistrazioneAggiunta` → `AvviaElaborazione` (queued as `in_attesa`, processed
  in order) [user, Q-6] → side-effect wired in the aggiungi-registrazione application-service

## Tactical model — Trascrizione — every row names the consumer, or it is not written
- **Aggregates / entities:**
  - `Elaborazione` (root, one per run) guards `StatoElaborazione`, `registrazioneId`, failure reason.
    → manifest `aggregate` block (elaborazione) + architect decision (long-running execution:
    background worker/process, ML adapters from spikes `scelta-diarizzatore`,
    `scelta-asr-code-switching`, `allineamento-parole-voci`, `packaging-modelli-desktop`)
  - `Trascritto` (root, one per `Registrazione`, identity = `registrazioneId`) guards the `Voce`
    entities and the `Segmento` entities; `Revisione` operations are methods of this root.
    → manifest `aggregate` block (trascritto) + architect decision (persistence of the source of truth)
- **Invariants:**
  - [INV-3] `StatoElaborazione` moves only `in_attesa → in_corso → completata | fallita`;
    `completata` and `fallita` are terminal. → invariant-test on the elaborazione aggregate block
  - [INV-4] per `Registrazione`: at most one `Elaborazione` in `in_attesa | in_corso`, at most one
    `completata`; a new `Elaborazione` can be started only if every previous one is `fallita`
    (retry only after `fallita`; no re-run after `completata`) [user].
    → set rule across `Elaborazione` instances: test on the **avvia-elaborazione application-service
    block** + repository uniqueness (architect), not an aggregate-local test
  - [INV-5] a `Trascritto` exists iff its `Registrazione` has a `completata` `Elaborazione`; it is
    created atomically with the transition to `completata`. Hence `Revisione` (and every
    `Parlanti` operation on its `Voce`s) is possible only on a `completata` `Elaborazione`.
    → test on the avvia-elaborazione application-service block (atomic completion)
  - [INV-6] every `Segmento` belongs to exactly one existing `Voce` of the same `Trascritto`; every
    `Voce` has ≥ 1 `Segmento` — a `Voce` left with zero `Segmento`s (by `unire` or by `riassegnare`
    its last `Segmento`) is removed. → invariant-test on the trascritto aggregate block
  - [INV-7] within a `Voce`, `Segmento`s are ordered by `inizio`; each has `inizio < fine` within the
    duration of the `Registrazione`. `Segmento`s MAY overlap in time, also within one `Voce`
    (overlapping speech, or brought together by `unire`/`riassegnare`); no `Revisione` is ever
    blocked because of an overlap [user, Q-4]. Ties on `inizio` are broken by `segmentoId`
    (deterministic order). → invariant-test on the trascritto aggregate block
  - [INV-8] `Revisione` conserves the `Segmento`s: the set of `Segmento`s (ids, intervals, text) is
    identical before and after any `Revisione`; only their `Voce` changes. The text of a `Segmento`
    is immutable in v1. → invariant-test on the trascritto aggregate block
  - [INV-9] `unire(A, B)`: A ≠ B, both `Voce`s of the same `Trascritto`; afterwards every `Segmento`
    of B is on A and B no longer exists (A survives, B is removed — direction chosen by the user).
    → invariant-test on the trascritto aggregate block
  - [INV-10] `dividere(A, S)`: S is a subset of A's `Segmento`s, non-empty and not all of them;
    afterwards S forms a NEW `Voce` (new `voceId`, next label number), the rest stays on A.
    → invariant-test on the trascritto aggregate block
  - [INV-11] `riassegnare(segmento, destinazione)`: destinazione is an existing `Voce` of the same
    `Trascritto` other than the current one, or a NEW `Voce`; the source `Voce` is removed if emptied
    ([INV-6]). → invariant-test on the trascritto aggregate block
  - [INV-12] `voceId` is never reused within a `Trascritto`; the "Voce n" label number is fixed at
    creation. → invariant-test on the trascritto aggregate block
- **Domain events:**
  - `ElaborazioneAvviata` → `read-model` Registrazioni del Progetto (stato)
  - `ElaborazioneCompletata` → `read-model` Registrazioni del Progetto + `read-model` Trascritto
    (view for `Revisione` and identification) + Documento `Rigenerazione` policy (first `Documento`,
    labels "Voce n")
  - `ElaborazioneFallita` → `read-model` Registrazioni del Progetto (reason + retry offered)
  - `VociUnite` (registrazioneId, voce sopravvissuta A, voce rimossa B) → Parlanti policy [INV-21] +
    Documento `Rigenerazione` policy + `read-model` Trascritto
  - `VoceDivisa` (registrazioneId, A, nuova voce A', segmenti spostati) → Parlanti policy [INV-21] +
    Documento `Rigenerazione` policy + `read-model` Trascritto
  - `SegmentoRiassegnato` (registrazioneId, segmento, da, a, da rimossa?, a nuova?) → Parlanti policy
    [INV-21] + Documento `Rigenerazione` policy + `read-model` Trascritto
- **Commands (+ actor):**
  - `AvviaElaborazione` (actors: the `RegistrazioneAggiunta` policy — automatic queueing — and the
    utente only as retry after `fallita` [user, Q-6]) → `application-service` block (orchestrates the
    local pipeline through ports: audio decoding, diarization, ASR, alignment into `Segmento`s)
  - `UnisciVoci` (actor: utente — directly, or one click on a `Proposta di unione`)
    → `application-service` block
  - `DividiVoce` (actor: utente) → `application-service` block
  - `RiassegnaSegmento` (actor: utente) → `application-service` block
- **Policy:** at application start, an `Elaborazione` found `in_corso` with no live run (app quit
  or crashed) → `fallita` (so retry is offered, [INV-4] holds).
  → side-effect at startup in the avvia-elaborazione application-service block

## Tactical model — Parlanti — every row names the consumer, or it is not written
- **Aggregates / entities:**
  - `Parlante` (root) guards `Nome`, `TipoParlante`, `StatoParlante`, `progettoId` and its
    `ImprontaVocale` entities (≥ 0, one per contributing `VoceRef`, never averaged).
    → manifest `aggregate` block (parlante) + architect decision (local storage of biometric
    embeddings)
  - `Attribuzione` (root, one per `VoceRef`) guards `VoceRef → parlanteId` (+ `progettoId`). Keyed by
    `VoceRef`, so "at most one `Parlante` per `Voce`" is structural.
    → manifest `aggregate` block (attribuzione)
  - `Galleria`, `Proposta`, `Candidato`, `Fascia`, `EstrattoAudio`, `Proposta di unione` are
    **computed, not stored as truth** [user] → `read-model` blocks below, no aggregate.
- **Invariants:**
  - [INV-13] `StatoParlante = eliminato` ⇒ zero `ImprontaVocale`s; `eliminato` is terminal: no
    rinomina, no promozione, no new `Attribuzione` (sole, policy-only exception: in `unire` the
    tombstone `Attribuzione` of the removed `Voce` is re-keyed onto the surviving one — [INV-21],
    ADR 0012 Amendment (b); no `ImprontaVocale` is created); its `Nome` and past `Attribuzione`s are
    kept (name-only tombstone) [user]. → invariant-test on the parlante aggregate block
  - [INV-14] a `Parlante` holds at most one `ImprontaVocale` per `VoceRef`; `ImprontaVocale`s are
    kept individually (adding one never replaces or averages the others).
    → invariant-test on the parlante aggregate block
  - [INV-15] an `ImprontaVocale` from `VoceRef` v on `Parlante` P exists iff a confirmed
    `Attribuzione(v) = P` exists and P is `attivo`; a `Proposta` never writes to the `Galleria`.
    Consequence: changing an `Attribuzione` from P to Q moves the evidence (P's `ImprontaVocale` for v
    removed, Q's derived); if P is thereby left without any `Attribuzione`, [INV-25] applies.
    Existence is transactional; freshness after a `Revisione` is eventual (ADR 0012 (b)): the row
    stores its `SorgenteImpronta.chiave` + model id and, if stale, is refreshed after commit by
    `RiallineaImpronte`. Every print is extracted from `SorgenteImpronta` only, never inside a
    transaction.
    → cross-aggregate (`Attribuzione` + `Parlante`, same local transaction):
    test on the **conferma-attribuzione application-service block**
  - [INV-16] `Nome` is unique among the `attivo` `Parlante`s of the same `Progetto` (compared trimmed
    and case-insensitively); an `eliminato`'s `Nome` is reusable [user]. Strict — no exemption: it
    holds on creation, rinomina, promozione AND on provisional "Ospite del …" names (made unique by
    the numeric suffix of [INV-19]) [user, Q-1].
    → set rule: test on the crea/rinomina/promuovi/conferma-attribuzione application-service blocks
    + repository uniqueness (architect)
  - [INV-17] an `Attribuzione` targets only an `attivo` `Parlante` of the SAME `Progetto` as the
    `Registrazione`, and only a `Voce` of an existing `Trascritto` ([INV-5]) (policy-only exception:
    [INV-21] `unire` re-keying of a tombstone `Attribuzione`).
    → test on the conferma-attribuzione application-service block (fake port on Trascrizione)
  - [INV-18] promozione: only `occasionale → ricorrente`; the `ImprontaVocale`s are unchanged; only
    `TipoParlante` and (optionally) `Nome` change. → invariant-test on the parlante aggregate block
  - [INV-19] skipping a `Voce` (`SaltaVoce`) = confirming it as a NEW `occasionale` `Parlante` with
    provisional `Nome` "Ospite del <DataRegistrazione>"; if that `Nome` is already taken by an
    `attivo` `Parlante` of the `Progetto`, the first free of "Ospite del <data> (2)", "(3)", … is
    used [user, Q-1]. The `Nome` is stored at creation and never follows a later
    `DataRegistrazione` change [user, Q-5]. Its `ImprontaVocale` is kept [user].
    → test on the salta-voce application-service block
  - [INV-20] `Proposta` for a `Voce`: `Candidato`s are only `attivo` `Parlante`s of the same
    `Progetto` with ≥ 1 `ImprontaVocale`; ranked `ricorrente` first, then by `Fascia`
    (`forte > debole > nessuna`) from `SoglieFascia`; every `Candidato` comes with an
    `EstrattoAudio`; no numeric score leaves the read-model [user].
    → view test on the proposta `read-model` block
  - [INV-21] after a `Revisione`, per affected `VoceRef`: a removed `Voce` loses its `Attribuzione`
    and the `ImprontaVocale` derived from it (save the `unire` inheritance exception below); a surviving/changed `Voce` with an `Attribuzione`
    keeps its `ImprontaVocale` row in the `Revisione`'s transaction (now possibly stale); it is
    re-derived from its current `SorgenteImpronta` after commit by `RiallineaImpronte`; a NEW `Voce` A' (from `dividere` /
    `riassegnare`) starts WITHOUT `Attribuzione` and gets its own `Proposta`, while A keeps its
    `Attribuzione` [user, Q-2]. In `unire(A, B)` with A and B attributed to DIFFERENT `Parlante`s,
    A's `Attribuzione` wins; B's `Attribuzione` and the `ImprontaVocale` derived from B are dropped
    [user, Q-3]. Exception (amended 2026-09-23, user decision): in `unire(A, B)` with the removed B
    attributed to `Parlante` P and the surviving A NOT attributed, A INHERITS B's `Attribuzione` to P
    (B's `Attribuzione` is re-keyed to A; B's `ImprontaVocale` row of P is re-keyed to A
    in-transaction, keeping its `sorgente_impronta` — so stale — and refreshed after commit; if P is
    `eliminato` only the tombstone `Attribuzione` is re-keyed, no row [ADR 0012 (b) point 4, user]),
    so P is not left without an `Attribuzione` and [INV-25] does not fire. Both attributed to the
    same P → A keeps its own row (stale, refreshed after commit), B's row and `Attribuzione` go.
    The revisione-policy never decodes nor extracts (ADR 0012 enforced_by). A `Parlante` thereby left without any `Attribuzione`
    → [INV-25].
    → test on the Parlanti revisione-policy application-service block
  - [INV-25] a `Parlante` left without any `Attribuzione` (after a `Revisione` or a changed
    `Attribuzione`): if `occasionale` it ceases to exist entirely (nothing references it — no
    `Documento` shows it, and by [INV-15] it has zero `ImprontaVocale`); if `ricorrente` it is kept
    with its other `ImprontaVocale`s [user, Q-3]. This is NOT `Eliminazione del Parlante` (no
    tombstone). → test on the revisione-policy and conferma-attribuzione application-service
    blocks
  - [INV-22] two `Voce`s of the SAME `Registrazione` attributed to the same `Parlante` is allowed,
    never blocked, never auto-merged; it yields a `Proposta di unione` (A, B) while the condition
    holds [user]. → view test on the proposta-di-unione `read-model` block
- **Domain events:**
  - `AttribuzioneConfermata` (VoceRef, parlanteId, precedente parlanteId?) → Documento
    `Rigenerazione` policy + `read-model` identificazione della Registrazione (Voce → Nome) +
    `read-model` Proposta di unione
  - `ParlanteCreato` → `read-model` Parlanti del Progetto (Galleria view: Nome, TipoParlante,
    StatoParlante, count of `ImprontaVocale`s)
  - `ParlanteRinominato` → Documento `Rigenerazione` policy (every `Documento` whose `Registrazione`
    has an `Attribuzione` to it) + `read-model` Parlanti del Progetto
  - `ParlantePromosso` → `read-model` Parlanti del Progetto + Documento `Rigenerazione` policy (only
    if the `Nome` changed)
  - `ParlanteEliminato` → `read-model` Parlanti del Progetto; NO `Documento` change [user]
  - `ImpronteRiallineate` (registrazioneId) — published after `RiallineaImpronte` commits → `Proposta`
    cache invalidation + `AggiornamentiVista`; NOT Documento (prints do not change it) [ADR 0012 (b)]
- **Read-models (computed):**
  - `Proposta` per `Voce` (from the `Galleria`, the embedding of the not-yet-attributed `Voce` —
    transient, never written to the `Galleria` — and `SoglieFascia`, output of spike
    `impronta-vocale-affidabilita`) → `read-model` block (consumer: identification UI)
  - `EstrattoAudio` for a `Voce` or for the source `Voce` of an `ImprontaVocale` (intervals via the
    Trascrizione port + audio via the Progetto port) → `read-model` block
  - `Proposta di unione` per `Registrazione` → `read-model` block (consumer: Revisione UI, one click
    → `UnisciVoci`)
- **Commands (+ actor):**
  - `ConfermaAttribuzione` (VoceRef, existing `parlanteId` OR new `Nome`) (actor: utente — accepting
    a `Candidato`, choosing another `Parlante`, naming a new one, or correcting a wrong one). A
    `Parlante` newly named here is `ricorrente` by default; the user may choose `occasionale`
    [user, Q-7]. → `application-service` block
    Extracts the print from `SorgenteImpronta` BEFORE the transaction, then re-reads the `Voce`
    in-transaction; expected error `VoceCambiata` if its `SorgenteImpronta` changed meanwhile
    (nothing written) [ADR 0012 (b)].
  - `SaltaVoce` (VoceRef) (actor: utente) → `application-service` block (same
    extract-then-transaction shape; expected error `VoceCambiata`)
  - `RiallineaImpronte` (registrazioneId) (actor: sistema — after commit of a `Revisione`, coalesced,
    retried) and `RiallineaTutteLeImpronte` (progettoId) (actor: sistema — at project open, after
    `RecuperaElaborazioniInterrotte`): re-derive stale prints only (sorgente or model mismatch),
    extraction outside any transaction, compare-and-set UPDATE, never INSERT; emits
    `ImpronteRiallineate` → `application-service` block (riallinea-impronte)
  - `RinominaParlante` (actor: utente) → `application-service` block
  - `PromuoviParlante` (actor: utente) → `application-service` block
  - `EliminaParlante` (actor: utente, explicit confirmation — privacy right) → `application-service`
    block (purges every `ImprontaVocale` in the same transaction as the tombstone)
- **Policy:** on `VociUnite` / `VoceDivisa` / `SegmentoRiassegnato` → apply [INV-21]
  → `application-service` block (revisione-policy) consuming Trascrizione events in-process
  (structural part only, in-transaction); **after commit** on the same events → `RiallineaImpronte`
  (registrazioneId) → adapter block (abbonato-riallineamento-impronte) [ADR 0012 (b)]

## Tactical model — Documento — every row names the consumer, or it is not written
- **Aggregates / entities:** none — `Documento` is a pure projection and owns no source of truth.
  → manifest `read-model` block (documento generator) + side-effect block (writes the `.md` file;
  location and overwrite strategy: architect decision)
- **Invariants (projection rules):**
  - [INV-23] `Documento` = deterministic function of (`Trascritto`, `Attribuzione`s, `Nome`s):
    regenerating with unchanged inputs yields byte-identical output; the `.md` is never read back.
    → view test on the documento `read-model` block
  - [INV-24] every `Voce` renders as the `Nome` of its attributed `Parlante` (also when `eliminato`);
    a `Voce` without `Attribuzione` renders as "Voce n"; `Segmento`s appear in time order (`inizio`)
    across `Voce`s, text verbatim (mixed IT/EN untouched). → view test on the documento `read-model`
    block
- **Domain events:** none with a consumer (a "Documento rigenerato" event has no reader in v1 —
  not written).
- **Commands (+ actor):** none — `Rigenerazione` is only triggered by policy.
- **Policy:** on `ElaborazioneCompletata`, `VociUnite`, `VoceDivisa`, `SegmentoRiassegnato`,
  `AttribuzioneConfermata`, `ParlanteRinominato`, `ParlantePromosso` (if `Nome` changed) →
  `Rigenerazione` of every affected `Documento` (one per `Registrazione`). `ParlanteEliminato` →
  no `Rigenerazione` [user]. → side-effect block (rigenerazione policy)

## Amendments 2026-09-23 (build-manifest reconciliation — user checkpoint, all [user])
Pinned in `building-blocks.yaml`; the rows above are otherwise unchanged.
- **R6 `titolo`:** `Registrazione` also guards `titolo` = the source file name without extension,
  set at `AggiungiRegistrazione`, immutable. It crosses to Trascrizione/Parlanti/Documento in `RegistrazioneVista`.
  *(Amended 2026-09-23, user decision:)* `titolo` is **unique per `Progetto`** — a set rule checked by
  `AggiungiRegistrazione` inside its transaction (one writer process per project, ADR 0010 `.lock`): on a clash
  of the file-safe case-insensitive key it appends " (2)", " (3)"… once, never changed afterwards; this keys the
  `Documento` file name (manifest `servizi-registrazione` AC-322..324, `documento` AC-320/321).
- **R2 policy placement:** "on `RegistrazioneAggiunta` → `AvviaElaborazione`" is realized as a
  synchronous subscriber in Trascrizione (same transaction), NOT inside the aggiungi-registrazione
  service (Progetto must not depend on Trascrizione).
- **R17 AvviaElaborazione split:** `AvviaElaborazione` (queueing + retry, INV-4) and the internal
  commands `EseguiProssimaElaborazione` (pipeline, INV-5) and `RecuperaElaborazioniInterrotte`
  (the startup policy), actor: sistema.
- **R5 Documento policy:** also on `DataRegistrazioneModificata` (the `.md` file name contains the date;
  the new file is written, then the old one removed).
- **R7:** `VoceId` IS the label number n of "Voce n" (no separate `etichettaNumero`).
- **R21:** `SaltaVoce` emits `ParlanteCreato` + `AttribuzioneConfermata`. **R22:** `ParlantePromosso`
  carries `nome` + `nomeCambiato`.
- **R24 decisions:**
  - Voci are numbered by first appearance (the Voce that speaks first is Voce 1).
  - Zero speech / zero diarization turns → `Elaborazione` `fallita` with motivo "nessun parlato rilevato".
  - The same source file added twice → two distinct `Registrazione`s, never blocked.
  - Re-confirming the same `Parlante` for a `Voce` → no-op, no event.
  - `SaltaVoce` on an already-attributed `Voce` is not offered and is rejected (`VoceGiaAttribuita`);
    use "cambia".
  - Guest name format: "Ospite del 12/09/2026" (dd/MM/yyyy).
  - `EstrattoAudio` = the 2–3 LONGEST `Segmento`s of the `Voce`, about 10 s in total (≤ 10 000 ms,
    the last clipped), played as a sequence of intervals. *(Tightened by ADR 0012 (b): the same
    selection function as `SorgenteImpronta` — `Segmento`s ≥ 1 000 ms, longest first, ≤ 3, budget
    10 000 ms, crossing interval trimmed from its start, time order; fallback the single longest.)*
  - A `Candidato`'s `Fascia` = the BEST over the `Parlante`'s `ImprontaVocale`s; ties (same
    `TipoParlante` and `Fascia`) are ordered by `Nome` alphabetically.
  - `Documento` format: `# <titolo>`, a date line "Registrata il dd/MM/yyyy", then one line per
    `Segmento` `**Nome** (mm:ss): testo`; consecutive `Segmento`s of the same `Voce` stay separate lines.
- **R25 (flag):** `INV-25` is realized with a repository `rimuovi` — the only physical deletion of a
  `Parlante` (the "no deletion" aggregate rule does not apply: nothing references it).
