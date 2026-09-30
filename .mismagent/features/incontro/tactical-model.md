# Tactical model — incontro

> Canonical names come from `.mismagent/context-map.md` (amended 2026-09-30 by feature `incontro`);
> this file never renames them. Decisions: [decisions.md](decisions.md) D-0001..D-0004, D-0006..D-0009
> (D-0005 superseded by D-0008); brief: [product-brief.md](product-brief.md). Single side (`app`):
> every boundary is in-process.
> Invariant ids are local to this file: prefix `[INV-I…]` (no collision with INV-1…28 of
> trascrizione-con-parlanti or INV-S1…S10 of sintesi).
> Numbering for the architect: next free ADR (0031 on this tree) and migration `7.sqm`, to be checked against `main`.
> **Prerequisite:** spike `contesto-lungo` closes before the Sintesi part below is modeled.
> Spikes `voci-tra-parti` and `ora-di-inizio` are central.

## Seeds for the tactical
<!-- The explore→model handoff: the analyst writes it, the tactical-modeler absorbs it and empties it. -->

### Progetto
- `Incontro` (root, identity `incontroId`, references `progettoId`) has ≥ 1 `Parte`. Order, title, date and numero della parte are DERIVED at read time and never stored.
- `Registrazione` gains `incontroId`, set at import and **immutable** (like INV-1's `progettoId`), and `OraDiInizio` (VO, user-editable) [user D-0008, D-0009].
- [INV-I1] Every `Registrazione` is a `Parte` of exactly one `Incontro` of the same `Progetto`; an `Incontro` has ≥ 1 `Parte`, so an empty `Incontro` never exists. It ceases to exist with its last `Parte` (`EliminaRegistrazione`).
- [INV-I2] The `Parte`s of an `Incontro` are ordered by (`DataRegistrazione`, `OraDiInizio`). The tie-break is for the tactical modeler (e.g. `aggiunta_alle`, then id). The initial `OraDiInizio` when the file gives none comes from spike `ora-di-inizio`.
- [INV-I3] A 1-part `Incontro` looks like today's `Registrazione`: same views, no "parte" label, same data after migration. The only deliberate differences are D-0007: `Voce` numbers are never reused, and a `Ritrascrivere` makes the `Riassunto` `superato` instead of deleting and re-enqueuing it. It is a view test on every read-model touched plus a migration test.
- Commands (actor: utente):
  - `AggiungiRegistrazione` is amended: it imports **one or several files into a target `Incontro`**, either new (created by this import, in the same transaction) or existing [user D-0008]. The `titolo` rules are unchanged.
  - A command to edit `OraDiInizio`, alongside `ModificaDataRegistrazione` [user D-0009].
  - There is no attach, separate, merge or move command. A misplaced `Parte` is fixed by `EliminaRegistrazione` and re-importing it.
- Events (names for the tactical modeler): an `Incontro` created; `Parte`s added to an `Incontro` (from the import); the start time changed; `RegistrazioneEliminata` gains `incontroId`. Their readers are the read-models (list of `Incontro`s) and Sintesi (`superato` is derived, so it may need only the view refresh).
- Migration: one `Incontro` per existing `Registrazione`, its `Riassunto` moved to it, `OraDiInizio` initialized by the fallback rule of spike `ora-di-inizio` (or left empty until then; for the architect).
- UX note: importing several files into a **new** `Incontro` makes ONE `Incontro` with several `Parte`s, whereas today each file became its own recording. The import screen must say which `Incontro` the files go to.

### Trascrizione
- `Voce` becomes `Incontro`-scoped (D-0002), so the Revisione consistency boundary grows to the `Incontro`: `unire`/`dividere`/`riassegnare` across `Parte`s must be atomic, and INV-6 ("every `Voce` has ≥ 1 `Segmento`") now spans `Parte`s. Seed: a root holding the "Voci dell'Incontro" (with the number counter) and the `Segmento` → `Voce` assignment of every `Parte`. Each `Trascritto` stays per `Registrazione` and keeps its `Segmento`s (ids, intervals, text, INV-8). The split between roots is for the tactical modeler.
- [INV-I4] `Voce n` is unique in the `Incontro`, and a number is **never reused** in that `Incontro`, even when no `Voce` is left [user D-0007]. This amends ADR 0018 [INV-12] (reuse across generations, "renumbered from 1"), for the architect.
- [INV-I5] The completion of an `Elaborazione` of one `Parte` writes only that `Parte`'s `Segmento`s, atomically, as in ADR 0018.
  - A first transcription adds the `Parte`'s new `Voce`s to the `Incontro`.
  - A `Ritrascrivere` also removes that `Parte`'s old `Segmento`s from their `Voce`s. `Voce`s left empty are removed; `Voce`s with `Segmento`s in other `Parte`s keep their identity.
  - New `Voce`s are never auto-joined unless spike option (b) is chosen.
- [INV-I6] On `RegistrazioneEliminata` of a non-last `Parte`, the existing synchronous Trascrizione subscriber (ADR 0020) also removes that `Parte`'s `Segmento`s from the `Incontro`'s `Voce`s and the emptied `Voce`s. It publishes the `Voce`-level consequences for Parlanti. The veto on an open `Elaborazione` is unchanged, per `Parte`.
- [INV-I7] A `Revisione` may relate `Voce`s or `Segmento`s of different `Parte`s of the same `Incontro`, never of two `Incontro`s.
- Open for tactical + ux: editing (Revisione, naming) of an `Incontro` while one `Parte` has a re-run queued or running. ADR 0018 (b) makes S3 read-only per `Registrazione`, and here `Voce`s span `Parte`s. The lean is read-only for the whole `Incontro` during a re-run of any `Parte`, but not during a first transcription of a newly imported `Parte`, which only adds `Voce`s.
- `TrascrittoSostituito` stays per `Registrazione`. Its consumers change: Parlanti purges only the prints sourced from that `Parte` and the `Attribuzione`s of emptied `Voce`s; Sintesi no longer deletes (see below).

### Parlanti
- `VoceRef` = (`incontroId`, `voceId`), no longer (`registrazioneId`, `voceId`). It keys `Attribuzione` (INV-17 "same `Progetto`" is unchanged), and the migration maps 1:1 because each `Registrazione` has its own `Incontro`. No re-keying is ever needed after import (membership is immutable, numbers are never reused).
- [INV-I8] An `ImprontaVocale` is sourced from ONE `Parte`: at most one print per (`Parlante`, `VoceRef`, `Parte`), which amends INV-14. Every purge by `Registrazione` (Ritrascrivere, Elimina) is exact. Derived from ADR 0020 INV-28 / ADR 0009; the architect confirms.
- INV-22 / `Proposta di unione`: two `Voce`s of the same `Incontro` on one `Parlante`, across `Parte`s. This is the name-driven cross-part join, available whatever `voci-tra-parti` decides.
- INV-19 "Ospite del <data>" uses the `Incontro`'s date (first `Parte`).
- INV-21 / INV-25 policies extend to the `Voce`-level events of Ritrascrivere and elimination of a `Parte`.
- `Frase di riferimento` / "Riassegna per somiglianza": scope is the `Incontro`, falling back to per-`Parte` if `voci-tra-parti` shows similarity unreliable across files.
- `EstrattoAudio` of a `Voce` may concatenate `Segmento`s of different `Parte`s (different files). This is for the architect (audio port per `Registrazione`).
- Pending spike (a): a cross-part proposal between unattributed `Voce`s by print similarity. It is a read-model, never automatic.

### Sbobinatura
- Still one per `Registrazione`, a pure projection. INV-24 resolves names through the `Incontro`'s `Attribuzione`s, and "Voce n" labels are `Incontro` numbers.
- `Rigenerazione` fan-out: a change on a `Voce` regenerates the `Sbobinatura` of every `Parte` it spans. Importing a new `Parte` changes no other `Sbobinatura`. INV-23 (deterministic) is unchanged.

### Sintesi (model only after `contesto-lungo` closes)
- `Riassunto` is re-keyed from `registrazioneId` to `incontroId`. INV-S2 and INV-S3 apply per `Incontro`. The 6.sqm table is keyed by `registrazione_id`, so a forward-only migration re-keys it (ADR 0006). This is a redesign of the schema, not a widening (D-0001).
- `Fonte` = (`registrazioneId`, `segmentoId`), stored by `Registrazione` identity. "parte n" is derived at display; a 1-part `Incontro` shows no "parte".
- [INV-I9] Riassumi precondition, per `Incontro`. It is the **analyst's default, which the user may revisit** (A5 unanswered):
  - every `Parte` has a `Trascritto`;
  - no `Elaborazione` of any `Parte` is open;
  - INV-S2 holds;
  - the concatenated input fits `LimiteIngresso`.
- [INV-I10] `Verifica delle fonti` per `Parte`:
  - each `Fonte`'s `Registrazione` is a `Parte` of the `Incontro` as read for the run, and its `segmentoId` exists in that `Parte`'s `Trascritto`;
  - speakers are `Voce`s of the `Incontro`;
  - a `PuntoChiave` speaker is among the `Voce`s of its `Fonte`s.
- [INV-I11] `superato`, derived from a structure recorded at run time. The structure is:
  - the ordered list of `Parte`s;
  - the `Trascritto` generation of each `Parte`;
  - the `segmentoId → voceId` assignment of each `Parte`.

  It is `superato` if any of these differs. So it covers a revision, **every completed (re)transcription of any `Parte`, 1-part included** [user D-0007], a reorder by a date/time edit [D-0009], an elimination of a `Parte`, and an import of a `Parte` [D-0008].
  - Because `Voce` numbers are never reused, a re-transcription always changes the assignment, but the generation is kept as the explicit signal.
  - It is never regenerated automatically (D-0004, D-0007).
- [INV-I12] Membership or order change while a `Riassunto` of the `Incontro` is `in_attesa`/`in_corso` is **allowed** [user-confirmed lean (a)].
  - A queued one reads the `Incontro` when it starts.
  - A running one completes **born `superato`**, as INV-S7 does for a Revisione during the run.
  - An `Incontro` that ceased to exist during the run gets nothing written (CAS, as today's INV-S8).
- [INV-I13] A reference (`Responsabile`, `PuntoChiave` speaker, token in text, a `Fonte`'s speaker) to a `Voce` that no longer exists in the `Incontro` renders as "a `Voce` no longer present". It never resolves to a `Nome` or to another person [user D-0007]. This is a view test on the riassunto-vista read-model; the names port must tell "no longer present" from "unattributed".
- INV-S8 is amended:
  - on `TrascrittoSostituito` of any `Parte` (1-part included) it is no longer delete + auto re-enqueue [D-0004, D-0007];
  - on `RegistrazioneEliminata` of a non-last `Parte`, the `Riassunto` is not deleted (D-0003).

  This conflicts with ADR 0020 INV-28 / ADR 0024 and ADR 0021 §6, so it is for the architect.
- Shared queue (ADR 0023): the `Riassunto` source item and `PosizioniNellaCoda.riassunti` key by `incontroId`.
