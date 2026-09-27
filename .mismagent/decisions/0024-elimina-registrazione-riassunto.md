---
scope: global
status: accepted
supersedes: null   # AMENDS ADR 0020 (§2 step 4 subscribers, §6 dialog text, §7 [INV-28], Consequences "Release"); pointer added there. Nothing of ADR 0020 is withdrawn.
amended: 2026-09-27   # see §4 dated note 2026-09-27 (R2 composition retired; subscriber order declared, ADR 0030)
closes_spike: null
enforced_by: null   # the fail-closed guarantee is structural (the IMMEDIATE FK riassunto → registrazione, presence-checked by ADR 0022's enforced_by); the rest is test + review
---
# 0024 — "Elimina registrazione" also deletes the `Riassunto` (amends ADR 0020)

## Context
ADR 0020 (user, 2026-09-25) makes deleting a `Registrazione` a hard delete across the contexts, in
one transaction, driven by the published `RegistrazioneEliminata` and its synchronous subscribers.
Feature `sintesi` adds a context whose rows are keyed by `registrazioneId`. The user decided
(context-map, Progetto → Sintesi) that deletion removes every `Riassunto`, queued ones included, for
privacy. The tactical model's [INV-S8] and its eliminazione-registrazione policy say the same, and
the tactical model flags the change to [INV-28] and to the dialog text as owed by the architect.
This ADR is that amendment, so that ADR 0020 is never edited silently.

## Decision

### 1. A third synchronous subscriber (ADR 0020 §2, step 4)
- `AbbonatoProgettoSintesi` (`:sintesi:adattatori ..eventi`, registered by the R3 composition before
  the first command) → **eliminazione-registrazione policy** (`:sintesi:applicazione ..politiche`):
  - `riassunti.rimuoviDiRegistrazione(registrazioneId)` removes every `Riassunto` of it in **any**
    state (`in_attesa`, `in_corso`, `pronto`, `fallito`) with its `riassunto_elemento` /
    `riassunto_fonte` rows;
  - it publishes `RiassuntoEliminato(registrazioneId)` if at least one existed.
  
  It returns Ok on an empty set.
- **It never vetoes.** An `in_corso` `Riassunto` does not block the deletion: the user is not asked
  to wait for a summary. Its run ends in the completion's compare-and-set, which finds no row and
  writes nothing ([INV-S8], ADR 0022 §4). After commit, `RiassuntoEliminato` lets `:avvio` cancel the
  LLM run best-effort (ADR 0023 §5).
- An `in_attesa` `Riassunto` is deleted and will never run: the queue reads its head inside the claim
  transaction (ADR 0023 §2).
- **Order:** the subscriber runs inside step 4, so before step 5's removal of the `registrazione`
  row. `riassunto.registrazione_id` is an **IMMEDIATE** FK (ADR 0022), so a composition that forgot
  this subscriber fails the delete, and the design fails **closed**. That is the same guarantee
  ADR 0020 has for the `elaborazione`/`trascritto` FKs.
- No file is involved: a `Riassunto` lives only in `progetto.db`. `eliminazione_in_sospeso` and
  `CompletaEliminazioniRegistrazioni` (ADR 0020 §4) are unchanged.

### 2. [INV-28] (ADR 0020 §7), amended text
> [INV-28] A `Registrazione` can be eliminata only if none of its `Elaborazione`s is `in_attesa |
> in_corso`. Its elimination removes, **in one transaction**:
> - the `Registrazione`;
> - every `Elaborazione` of it;
> - its `Trascritto` (`Voce`s, `Segmento`s);
> - every `Attribuzione` and `ImprontaVocale` keyed by one of its `VoceRef`s, followed by [INV-25];
> - **every `Riassunto` of it, in any state (its elements and `Fonte`s included)** *(ADR 0024)*.
>
> After the COMMIT no row keyed by its `registrazioneId` survives except its `eliminazione_in_sospeso` row. That row
> is removed once its files (`audio/`, `cache/audio/`, the `Documento`) are gone. A refused or failed elimination
> changes nothing.

An open `Riassunto` is not in the precondition: only an open `Elaborazione` vetoes.

### 3. Dialog text (ADR 0020 §6), amended — "with a `Trascritto`" variant only
> "Verranno cancellati il file audio copiato nel progetto, la trascrizione con le correzioni delle voci, i
> nomi dati alle voci, il documento, **il riassunto** e le impronte vocali ricavate da questa registrazione.
> Non si può annullare."

- The text is static. It names the `Riassunto` even when none exists, as it already does for the
  `Documento`. A `Riassunto` can exist only if a `Trascritto` exists, so the "no `Trascritto`"
  variant is unchanged.
- The second paragraph, the title, the buttons, the disabled captions and the race-backstop text are
  unchanged.

### 4. Release
ADR 0020 is R2 (`avvio-parlanti`). This amendment is **R3** (`avvio-sintesi`, ADR 0021 §10):
- the R3 composition registers **all three** synchronous subscribers (Trascrizione, Parlanti,
  Sintesi) before offering "Elimina…";
- an R2 composition never creates `riassunto` rows, so it needs no Sintesi subscriber.

A `progetto.db` that already holds `Riassunto`s, opened by an R2 composition (development only),
refuses the delete on the FK. It fails closed, and the error surfaces as an infra fault per ADR 0003.

*(2026-09-27, [ADR 0030](0030-composizione-unica-per-contesto.md) [user]; closes pending user decision 5)*
- **Compositions.** R0–R2 are retired as compositions, so the "R2 composition" case above no longer exists. The
  single composition always registers the three synchronous subscribers of `RegistrazioneEliminata`.
- **Subscriber order.** The order is now **declared**, as one list in `apriProgetto`: **Sintesi → Parlanti →
  Trascrizione**. It is today's effective order, unchanged.
  - Correctness does not depend on it. All three run in the command's single transaction, and a Trascrizione veto
    or any failure in any position dooms the whole unit (ADR 0020 §2, ADR 0012).
  - It is declared for reproducibility and review. AC-S143 asserts the list.
  - This also answers the §1 pre-release note ("subscriber order irrelevant"): irrelevant to correctness, pinned all
    the same.

## Consequences
- **Tests** (ids pinned by build-manifest):
  - the eliminazione-registrazione-sintesi policy block: every state removed, `in_corso` included;
    the empty set gives Ok; `RiassuntoEliminato` is published only when something was removed;
  - `esegui-riassunto`: the race "deleted while `in_corso`" gives no write and no event;
  - the `avvio-sintesi` e2e on real SQLite: the row counts of `riassunto*` are 0 after the delete;
    with the subscriber unregistered, the delete fails and nothing changes.
- **`:ui` `MessaggiErrore` / S2 dialog:** a one-line text change, in the S2 row-menu block owned by
  ADR 0020's delta.
- **Pointers:** ADR 0020 gains a dated line pointing here. `features/trascrizione-con-parlanti`'s
  manifest delta for ADR 0020 is not edited. This feature's manifest carries the new ACs.
- **Dependency:** ADR 0020's blocks (`RegistrazioneEliminata`, `5.sqm`) are being built on the
  sibling branch. This feature's subscriber block depends on them.
