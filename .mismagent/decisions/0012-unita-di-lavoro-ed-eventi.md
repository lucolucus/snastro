---
scope: global
status: accepted
supersedes: null
closes_spike: null
enforced_by: null
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
- **R2 — auto-start policy lives in Trascrizione.** `RegistrazioneAggiunta` is a published event of
  Progetto; a **synchronous** subscriber in `:trascrizione:adattatori` runs `AvviaElaborazione` in
  the same transaction (a `Registrazione` never exists without its queued `Elaborazione`). Progetto
  never depends on Trascrizione.
- **R12 — ML inside a command transaction, accepted.** `ConfermaAttribuzione`, `SaltaVoce` and the
  Parlanti revisione-policy extract one embedding (seconds) inside the transaction (single local
  writer). Every native call — pipeline and `EstrattoreImpronta` — is serialized by one mutex in
  `:ml-sherpa`/`:avvio`. The long `Elaborazione` pipeline still never holds a transaction.
- **R13:** Parlanti does not subscribe to `ElaborazioneCompletata` (no consumer).
- **R21/R22:** `SaltaVoce` publishes `ParlanteCreato` + `AttribuzioneConfermata`; `ParlantePromosso`
  carries `nome` + `nomeCambiato` so the Documento policy can apply "only if the Nome changed".
- Published-event types are pinned in `features/trascrizione-con-parlanti/building-blocks.yaml`
  (boundaries `eventi-progetto`, `eventi-elaborazione`, `eventi-revisione`, `eventi-parlanti`).
