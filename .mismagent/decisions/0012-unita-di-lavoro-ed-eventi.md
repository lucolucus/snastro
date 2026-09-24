---
scope: global
status: accepted
supersedes: null
closes_spike: null
enforced_by: "! grep -rnE --include='*.kt' 'snastro\\.(parlanti\\.applicazione\\.porte\\.(EstrattoreImpronta|DecodificatoreAudio)|kernel\\.CampioniAudio|parlanti\\.dominio\\.Impronta)([^A-Za-z0-9_]|$)' parlanti/applicazione/src/main | grep '/politiche/' | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\\*|/\\*)' | grep -q ."
amended: 2026-09-23   # see "Amendment 2026-09-23 (b)" — R12 premise superseded (option (c)); enforced_by added. "Amendment 2026-09-23 (c)" — R2 auto-start removed (ADR 0014 [user])
---
# 0012 — Unit of work and domain-event dispatch: invariant policies in-transaction, Rigenerazione after commit

## Context
Knob K1. Several invariants span aggregates/contexts inside one local database:
[INV-15] (`Attribuzione` + `Parlante` prints), [INV-21]/[INV-25] (Parlanti's reaction to
`VociUnite`/`VoceDivisa`/`SegmentoRiassegnato`), [INV-5] (`Trascritto` created atomically with
`completata`). `Documento` `Rigenerazione` is a derived projection ([INV-23], idempotent).

## Decision
- `:kernel` declares `UnitaDiLavoro` (port) + an in-process synchronous `DispatcherEventi`;
  `:persistenza`'s adapter implements it as one SQLDelight `transaction {}` per command.
- **Invariant-carrying policies run synchronously INSIDE the command's transaction** (the
  Parlanti revisione-policy reacting to Trascrizione events; conferma-attribuzione's print moves;
  the atomic `completata` + `Trascritto` write). If a policy fails, the whole command rolls back and
  returns `Esito.Errore` (ADR 0003).
- **`Rigenerazione` of `Documento` runs AFTER COMMIT** on a background coroutine, keyed by
  `registrazioneId` (coalesced), idempotent (byte-identical output for unchanged inputs) and
  **retried** on failure; at startup every `Documento` whose inputs changed since its last write
  (a persisted `documento_generato_versione` vs the `Trascritto`/names version) is regenerated.
- Cross-context events: the supplier's `applicazione` exposes **published events** (Published
  Language: kernel VOs + primitives) with the **same canonical names** as the domain events
  (e.g. `VociUnite`, in package `snastro.trascrizione.applicazione.eventi`), translated from them; the **consumer's `adattatori`** subscribes to them through the
  kernel `DispatcherEventi` and invokes the consumer's `applicazione` policy (e.g. Parlanti
  revisione-policy, Documento `Rigenerazione`). The supplier never knows the consumer; no context
  imports another's `dominio` (ADR 0002 edges).

## Consequences
- The ML pipeline never holds a DB transaction: `AvviaElaborazione` runs the pipeline outside, then
  commits the result (`completata` + `Trascritto`) in one short transaction.
- Discursive (code review): no invariant policy may be moved after commit; no `Rigenerazione` inside
  the transaction.

## Amendment 2026-09-23 (build-manifest reconciliation R2, R4, R12, R13, R21, R22)
- **R4 — no `documento_generato_versione`.** `:documento:*` may neither persist (ADR 0006) nor read
  back (ADR 0010), so the persisted version is dropped: at startup **every** `Documento` with a
  `Trascritto` is regenerated unconditionally (`RigeneraTuttiIDocumenti`, idempotent, cheap). The
  after-commit, coalesced, retried `Rigenerazione` is unchanged.
- **R2 — auto-start policy lives in Trascrizione.** *(SUPERSEDED by "Amendment 2026-09-23 (c)"
  below, following the user decision in ADR 0014. Kept as history.)* `RegistrazioneAggiunta` is a
  published event of Progetto; a **synchronous** subscriber in `:trascrizione:adattatori` runs
  `AvviaElaborazione` in the same transaction (a `Registrazione` never exists without its queued
  `Elaborazione`). Progetto never depends on Trascrizione.
- **R12 — ML inside a command transaction, accepted.** *(SUPERSEDED by "Amendment 2026-09-23 (b)"
  below — kept as history.)* `ConfermaAttribuzione`, `SaltaVoce` and the
  Parlanti revisione-policy extract one embedding (seconds) inside the transaction (single local
  writer). Every native call — pipeline and `EstrattoreImpronta` — is serialized by one mutex in
  `:ml-sherpa`/`:avvio`. The long `Elaborazione` pipeline still never holds a transaction.
- **R13:** Parlanti does not subscribe to `ElaborazioneCompletata` (no consumer).
- **R21/R22:** `SaltaVoce` publishes `ParlanteCreato` + `AttribuzioneConfermata`; `ParlantePromosso`
  carries `nome` + `nomeCambiato` so the Documento policy can apply "only if the Nome changed".
- Published-event types are pinned in `features/trascrizione-con-parlanti/building-blocks.yaml`
  (boundaries `eventi-progetto`, `eventi-elaborazione`, `eventi-revisione`, `eventi-parlanti`).

## Amendment 2026-09-23 (b) — bounded print source, no ML inside a transaction, Revisione split (supersedes the R12 premise)
**Why.** The R12 premise ("one embedding, seconds, inside the transaction") was false: the build
(code-review of `conferma-attribuzione`, `salta-voce`, `revisione-policy`, MED F4) showed that the
print was extracted from **all** of a `Voce`'s audio (a 40-minute speaker → minutes of native
work), that the Revisione policy re-derived **every** affected attributed `Voce` inside the
Revisione's transaction, and that the native Mutex (AC-236) could be awaited **while holding the
write transaction** for the whole length of an `Elaborazione`. Options (a) cap only, (b) move-only,
(c) = (a)+(b), (c′) = (a)+Mutex timeout were put to the user; **option (c) was chosen on
2026-09-23 [user]** (dispatch.log `(impronta-estrazione) decision`). The `unire` inheritance rule
and its eliminato case were decided the same day [user].

**1. Bounded source — `SorgenteImpronta` (pure, in `:parlanti:dominio`).**
- Every derivation of a `Voce`'s print — `ConfermaAttribuzione`, `SaltaVoce`, `RiallineaImpronte`
  and the transient `Proposta` print — reads audio **only** through
  `SorgenteImpronta.di(intervalliDellaVoce)`: the `Voce`'s `Segmento` intervals of **≥ 1 000 ms**,
  taken longest first (tie: earlier `inizioMs`), accumulated up to a total of
  **`BUDGET_IMPRONTA_MS = 30 000`** ms; the interval that crosses the budget is **trimmed from its
  start** (`[inizio, inizio + resto]`); the result is returned **in time order**. If no `Segmento`
  reaches 1 000 ms, the single longest `Segmento` is used (a `Voce` always has ≥ 1 interval, so a
  source always exists — [INV-15] never lacks an input).
- **`BUDGET_IMPRONTA_MS` is PROVISIONAL**: spike `impronta-vocale-affidabilita` calibrates it and
  records the final value in its closing ADR. Changing it later is safe by construction (point 3:
  every print becomes stale and is re-derived).
- `SorgenteImpronta.chiave` = canonical text encoding of its intervals in time order,
  `"<inizioMs>-<fineMs>"` joined by `","` (e.g. `"1200-5400,8000-15000"`) — the print's **source
  fingerprint**. Exact, collision-free, no hashing in the domain.
- **`EstrattoAudio` applies the same selection rule** with budget **10 000 ms** and **≤ 3
  intervals** (one shared pure function; supersedes the looser "2–3 longest, about 10 s" reading).

**2. No ML inside a transaction.** No `UnitaDiLavoro` transaction is open while
`DecodificatoreAudio` or `EstrattoreImpronta` run — commands and after-commit work alike.
- `ConfermaAttribuzione` / `SaltaVoce`: (i) outside any transaction, read the `Voce`
  (`LettoreVoci`), compute `s = SorgenteImpronta.di(...)`, decode `s.intervalli`, extract the
  `Impronta` (cheap refusals — `VoceGiaAttribuita`, `Voce`/`Trascritto` not found, re-confirm of the
  same `Parlante` — may be answered before extracting, to avoid useless native work); (ii) then open
  the transaction, **re-read the `Voce`**: if `SorgenteImpronta.di(...)` now differs from `s` →
  `Esito.Errore(ErroreParlanti.VoceCambiata(voceRef))`, **nothing written**; otherwise run every
  invariant check authoritatively ([INV-15]/[INV-16]/[INV-17]/[INV-19]/[INV-25]) and write the
  `Attribuzione` + the print row (with `s.chiave` and the extractor's model id) in the same
  transaction.
- An extraction/decoding failure happens **before** any transaction: nothing is written and the
  infra fault propagates per ADR 0003 (AC-86 holds trivially).
- [INV-15] **stays transactional** for commands: the print row of the attributed `Voce` is written
  in the same transaction as the `Attribuzione` (moves P → Q included).

**3. Revisione split — structure in-transaction, print freshness after commit.**
- The Parlanti revisione-policy (`..politiche`, synchronous subscriber, **inside** the Revisione's
  transaction) keeps **only the structural part** of [INV-21]/[INV-25]: remove the `Attribuzione`
  and the print of a removed/emptied `Voce`; `unire` precedence and inheritance (below); a new `Voce`
  starts unattributed; cease an `occasionale` left without `Attribuzione`s. It **never** decodes nor
  extracts (enforced_by).
- **Re-deriving the prints of surviving attributed `Voce`s** becomes **`RiallineaImpronte`**
  (application service in `:parlanti:applicazione` `..comandi`), run **after commit** by an
  `AbbonatoDopoCommit` in `:parlanti:adattatori` on `VociUnite` / `VoceDivisa` /
  `SegmentoRiassegnato`, **coalesced per `registrazioneId`**, **idempotent**, **retried** on failure
  (same discipline as `Rigenerazione`), and **also run at startup** over every print of the open
  project (`RiallineaTutteLeImpronte`), after the Elaborazione queue recovery.
- **Staleness.** Each `impronta_vocale` row stores `sorgente_impronta` (the `SorgenteImpronta.chiave`
  it was extracted from) and `modello_impronta` (the extractor's model id). A row is **stale** iff
  `sorgente_impronta ≠ SorgenteImpronta.di(current intervals of its Voce).chiave` **or**
  `modello_impronta ≠ EstrattoreImpronta.modello`. `RiallineaImpronte` touches stale rows only.
- **Per-`Voce` grouping.** For each stale row: decode + extract **outside** any transaction, then one
  short transaction per `Voce` that re-reads the row and the `Voce` and **UPDATEs the existing row
  only if** it still exists, its `(sorgente_impronta, modello_impronta)` still equals what was read
  before extracting, and the recomputed source still equals the extracted one (compare-and-set);
  otherwise it writes nothing (a later run converges). **It never INSERTs** — so it can never
  resurrect a print purged by `EliminaParlante`, a changed `Attribuzione` or [INV-25] (ADR 0009).
- **[INV-15] under the split.** Existence stays transactional, only **freshness** is eventual
  [user-accepted]: after a Revisione every surviving attributed `Voce` still has its print row (now
  possibly stale) in the same transaction; in `unire` with inheritance (below) the removed `B`'s row
  of `P` is **re-keyed** to `A` in-transaction (it keeps `B`'s `sorgente_impronta`, so it is stale by
  construction and refreshed after commit). A stale print may feed a `Proposta` until refreshed; a
  print of another model is **never compared** (the `Proposta` ignores rows whose `modello_impronta`
  differs from the extractor's until they are refreshed).

**4. [INV-21] `unire(A, B)` — inheritance, including an `eliminato` `Parlante` [user].** When only
the removed `B` is attributed (to `P`) and the surviving `A` is not, **`A` inherits `B`'s
`Attribuzione` to `P`**: `P` `attivo` → `B`'s print row is re-keyed to `A` (refreshed after commit);
`P` `eliminato` → the tombstone `Attribuzione` is re-keyed to `A` and **no print is created**
([INV-13]'s "zero prints" is untouched). This is an **explicit, policy-only exception** to [INV-13]
("an `eliminato` receives no new `Attribuzione`") and to [INV-17] ("an `Attribuzione` targets only
an `attivo` `Parlante`"): it is not a new attribution by the user but the **re-keying of an existing
one** onto the `Voce` that absorbed its audio, so the tombstone keeps naming that speech in the
`Documento` ([INV-24]). No command (`ConfermaAttribuzione`, `SaltaVoce`) may use it. Both attributed
to the same `P` → `A` keeps its own row (stale), `B`'s row and `Attribuzione` go; different
`Parlante`s → `A` wins as before.

**5. The native Mutex is always acquired OUTSIDE any transaction.** The Mutex serializing every
native call (pipeline and `EstrattoreImpronta`, AC-236) lives in the ML adapters/`:avvio`; since
point 2 forbids an open transaction around extraction, a wait on the Mutex never holds the SQLite
write lock.

**Enforcement.**
- `enforced_by` (prohibition, above): no source file under a `politiche` package of
  `:parlanti:applicazione` may reference `EstrattoreImpronta`, `DecodificatoreAudio`,
  `CampioniAudio` or `Impronta` (import or fully-qualified use; comment lines stripped;
  `ImprontaVocale` and `LettoreVoci` stay allowed). Wildcard imports — the obvious bypass — are
  already rejected by detekt's default `WildcardImport` (gate). Validated via `bash -c` on
  2026-09-23: **exit 0** on the integration line (main checkout — no `politiche` package yet) and on
  the `conferma-attribuzione`, `persistenza-schema`, `esegui-elaborazione`, `catalogo-registrazioni`
  worktrees; **exit 1** on the in-flight `revisione-policy` worktree (commit `c2f1b72` imports
  `DecodificatoreAudio`/`EstrattoreImpronta`/`Impronta` in `ApplicaRevisionePolitica.kt`) — red **by
  design**: that code is exactly what this amendment rewrites; the rule is green once the
  `revisione-policy` rework (option (c)) lands. Fixtures: FAIL on an `import` of each forbidden
  symbol and on a fully-qualified `snastro.kernel.CampioniAudio` use inside `politiche/`; PASS with
  the forbidden names only in KDoc/`//` comments, with `ImprontaVocale` imported, and with
  `EstrattoreImpronta` imported from `comandi/`.
- **Known blind spots → code review + tests:** a policy receiving the extraction through a locally
  declared interface or a function-typed parameter; a **command** calling `estrai`/`campioni` inside
  its `inTransazione { }` lambda (not greppable per line). Covered mechanically **by test**: the
  testFixtures fakes `EstrattoreImprontaFinta` / `DecodificatoreAudioFinta` **throw if invoked while
  the fake `UnitaDiLavoro` has a transaction open**, so every command's AC-tests fail on a
  regression.
- Discursive (code review): every audio read for a print goes through `SorgenteImpronta` (never
  `voce.intervalli` straight into `campioni`); `RiallineaImpronte` only UPDATEs with
  compare-and-set; no Mutex acquisition inside `inTransazione`.

**Consequences.**
- A `Conferma` can return `VoceCambiata` if a Revisione of the same `Voce` committed during its
  extraction; the UI shows it in plain language and the user retries (rare: one user, one window).
- Print freshness after a Revisione is eventual (seconds; after a crash, at the next startup).
- `BUDGET_IMPRONTA_MS` or embedding-model changes need no migration: all rows go stale and are
  re-derived by `RiallineaTutteLeImpronte`.
- **OPEN (deferred, not decided) [user]:** a `Conferma`/`SaltaVoce`/`Proposta` requested **during
  an `Elaborazione`** still waits for the native Mutex — potentially minutes, since the pipeline
  holds it per phase. It no longer blocks the database (point 5), but the user-facing wait is
  unresolved (candidates noted in discovery: Mutex timeout with "riprova dopo l'elaborazione",
  per-call Mutex release between pipeline chunks, a separate extractor session). To be decided
  before `avvio-coda-elaborazioni` / `schermata-registrazione` are built.
  **→ Decided 2026-09-24 by [ADR 0017](0017-attesa-mutex-estrazione.md)** (per-call Mutex hold,
  fair and interruptible wait; a visible, cancellable wait on the S3 card during diarization).

## Amendment 2026-09-23 (c): no automatic start on import (supersedes R2) [user]
**Decision (the user, 2026-09-23; recorded in ADR 0014, which is its single home).** No
`Elaborazione` is started automatically when a `Registrazione` is imported. The user starts one
from S2 with "Trascrivi", or with "Riprova" after a failure. Both actions take the optional
`NumeroPersone`, an integer from 1 to 10.

**What this supersedes.** The R2 bullet above. There is **no** synchronous subscriber to
`RegistrazioneAggiunta` in `:trascrizione:adattatori`. The guarantee "a `Registrazione` never exists
without its queued `Elaborazione`" is **withdrawn**: in R1, a `Registrazione` with no `Elaborazione`
is a normal state, shown as `NON_AVVIATA`.

**What is unchanged.**
- The unit-of-work and dispatch mechanism of this ADR, synchronous subscribers included (other
  invariant policies still use them).
- `RegistrazioneAggiunta` stays a published event of Progetto, with after-commit consumers only
  (view refresh). Progetto still never depends on Trascrizione.
- `AvviaElaborazione` still runs in its own `UnitaDiLavoro` transaction with the INV-4 checks.

**Consequences.** The `abbonato-registrazione-aggiunta` block and its ACs (AC-140, AC-141) leave the
manifest. The R1 composition stops registering the subscriber. This is folded by `build-manifest`.
