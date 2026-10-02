---
scope: global
status: accepted
supersedes: null   # partial, amended in place with dated pointers here: ADR 0020 §1 (event payload), §2 (subscribers and order of the deleting transaction), §6 (dialog for a non-last Parte), §7 ([INV-28]); ADR 0024 §1–§3 (the Riassunto is deleted only with the last Parte); ADR 0030 §2 (Parlanti's subscription moves to a nested Trascrizione event; the declared module order is unchanged)
closes_spike: null
decided: 2026-10-01 · architect (feature dispatch, incontro) on the user's D-0003 and D-0008 and the tactical model's [INV-I1], [INV-I6], [INV-I8b], [INV-I12b]
from_note: incontro D-0003
enforced_by: []   # the fail-closed guarantees are structural FKs (ADR 0034: incontro ← registrazione/voci_incontro/riassunto immediate, Parlanti → voce_incontro/voce deferred) checked by ADR 0034's presence check; ADR 0020's prohibition (Progetto never uses other contexts' queries) is unchanged
---
# 0038 — Eliminating a `Parte`: the `Incontro` ends with its last `Parte`; a non-last `Parte` leaves the `Riassunto` readable and `superato`

## Context
ADR 0020 (+ ADR 0024) deletes a `Registrazione` in one transaction driven by `RegistrazioneEliminata` and synchronous
subscribers, and promises in [INV-28] that no row keyed by its `registrazioneId` survives. With the `Incontro`:
- the `Voce`s and their `Attribuzione`s belong to the `Incontro`, so a `Parte`'s deletion removes only its `Segmento`s
  and the `Voce`s it leaves empty ([INV-I6]);
- the user decided that a non-last `Parte`'s deletion keeps the `Incontro`'s `Riassunto`, `superato`, with `Fonte`s
  naming the deleted `Parte` (D-0003, privacy cost accepted);
- the `Incontro` ceases with its last `Parte` ([INV-I1]).

## Decision

### 1. The event (amends ADR 0020 §1)
`RegistrazioneEliminata(registrazioneId, progettoId, titolo, dataRegistrazione, riferimentoAudio, incontroId,
incontroCessato: Boolean)`. `incontroCessato` is true iff no other `Parte` of the `Incontro` exists, read by
`EliminaRegistrazioneServizio` inside its transaction (`IncontroRepository.partiDi`).

### 2. The deleting transaction (amends ADR 0020 §2), `BEGIN IMMEDIATE`, in this order
1. `registrazioni.trova(id)` (absent → `RegistrazioneNonTrovata`); `incontroCessato` computed;
2. `evento = registrazione.elimina()`; the `eliminazione_in_sospeso` row (unchanged);
3. `eventi.pubblica(evento)` — synchronous subscribers in the declared module order (ADR 0030 §2, unchanged list
   **Sintesi → Parlanti → Trascrizione**):
   - **Sintesi** — if `incontroCessato`, removes every `Riassunto` of the `Incontro` (any state, never vetoes); otherwise
     nothing (ADR 0037 §7);
   - **Parlanti** — **no longer subscribes to `RegistrazioneEliminata`**;
   - **Trascrizione** — `ApplicaEliminazioneRegistrazionePolitica`: the veto on an open `Elaborazione` of THIS `Parte`
     (unchanged, `ElaborazioneGiaAperta`); its `Elaborazione`s go; then, if the `Parte` has a `Trascritto`,
     `rimuoviParte` on the Voci dell'Incontro root (its `Segmento`s leave their `Voce`s, emptied `Voce`s go) and it
     **publishes `TrascrittoEliminato(registrazioneId, incontroId, vociRimosse)`**, delivered depth-first to
     **Parlanti**'s synchronous subscriber ([INV-I8b] purge: every print sourced from this `Parte`, the `Attribuzione`s
     of `vociRimosse`, then [INV-25]); if `incontroCessato`, the root itself is removed (`voci_incontro` row and every
     remaining child row), after `rimuoviParte`;
4. `registrazioni.rimuovi(id)`;
5. if `incontroCessato`: `incontri.rimuovi(incontroId)` — after every row referencing it is gone (immediate FKs from
   `registrazione`, `voci_incontro`, `riassunto`: a forgotten subscriber fails here, closed);
6. COMMIT — the deferred FKs (`segmento → voce`, `attribuzione → voce_incontro`, `impronta_vocale → voce_incontro`, `→ voce`)
   fail it if the Parlanti purge missed a row.

**Why the Parlanti purge moves behind a Trascrizione event:** only Trascrizione knows which `Voce`s the `Parte`'s removal
emptied; Parlanti never reads Progetto membership to decide identity (context-map). The nested delivery is supported by
`DispatcherEventiInMemoria` (depth-first, any `Errore` dooms the unit). The module order list of ADR 0030 §2 is
unchanged; only the event Parlanti listens to changes. Race safety is ADR 0020 §2's, unchanged.

### 3. [INV-28], amended text (replaces ADR 0020 §7 as amended by ADR 0024 §2)
> [INV-28] A `Registrazione` (a `Parte`) can be eliminata only if none of its `Elaborazione`s is `in_attesa | in_corso`.
> Its elimination removes, **in one transaction**:
> - the `Registrazione` and every `Elaborazione` of it;
> - its `Trascritto`: its `Segmento`s leave the `Voce`s of the `Incontro`, and a `Voce` left with none ceases to exist;
> - every `ImprontaVocale` sourced from it (any `Voce`, any `Parlante`), and the `Attribuzione` of every `Voce` that
>   ceased, followed by [INV-25];
> - **if it was the last `Parte`:** the `Incontro`, its Voci dell'Incontro, and every `Riassunto` of the `Incontro` in any
>   state (elements and `Fonte`s included).
>
> After the COMMIT no row keyed by its `registrazioneId` survives, except: its `eliminazione_in_sospeso` row (removed once
> its files are gone), and — **only when the `Incontro` survives** — the `riassunto_fonte` rows of that `Incontro`'s
> `Riassunto`s that name it, together with the text derived from it, which stays readable in a `superato` `Riassunto`
> [user D-0003]. A refused or failed elimination changes nothing.

### 4. Files and views (ADR 0020 §3–§5, unchanged in substance)
The `Sbobinatura` of the deleted `Parte`, its audio copy and derived WAV go after commit by their owners. The other
`Parte`s' `Sbobinatura`s are regenerated by the `TrascrittoEliminato` fan-out when `Voce`s were removed (ADR 0035 §7),
since a `Voce` number may disappear from their legend. S3 of the deleted `Parte` navigates back to the list
(`NavigazioneProgetto.dimentica`); S3 of another `Parte` of the same `Incontro` reloads on the view refresh.

### 5. Dialog texts (amends ADR 0020 §6 / ADR 0024 §3; the home of these texts)
- **The last `Parte`, or a 1-part `Incontro`:** today's dialog, unchanged ([INV-I3]).
- **A non-last `Parte`:**
  - title: "Eliminare la parte n di «<titolo dell'incontro>»?";
  - text (with a `Trascritto`): "Verranno cancellati il file audio copiato nel progetto, la trascrizione con le correzioni
    delle voci, le voci che compaiono solo in questa parte, la sbobinatura e le impronte vocali ricavate da questa parte.
    Il riassunto dell'incontro resta leggibile ma diventa superato. Non si può annullare.";
  - second paragraph: "Le persone ricorrenti restano, con le impronte delle altre registrazioni. Le persone occasionali
    che compaiono solo qui spariscono. Il file originale fuori dal progetto non viene toccato." (unchanged);
  - text without a `Trascritto`: today's "senza trascritto" text, unchanged;
  - buttons, disabled captions and the race-backstop text: unchanged, per `Parte`.

## Rejected options
- **Deleting the `Riassunto` on any `Parte`'s deletion** (privacy first): the user chose `superato` (D-0003).
- **Parlanti keeping its `RegistrazioneEliminata` subscription and reading Progetto membership** to find the emptied
  `Voce`s: Parlanti would decide identity from membership, which the map forbids, and would duplicate [INV-6].
- **Removing the `Incontro` row from a Trascrizione or Sintesi subscriber**: the `Incontro` is Progetto's; ADR 0020's
  prohibition keeps every context to its own rows.
- **A separate "Elimina incontro" command**: the UX deletes `Parte`s one at a time and the `Incontro` ends with the last.

## Consequences
- `EliminaRegistrazioneServizio`, the Trascrizione elimination policy, the Parlanti subscriber wiring and the Sintesi
  policy change (blocks pinned by build-manifest). AC-S143's declared-list assertion stays as it is (module order
  unchanged); a new composition test asserts that `TrascrittoEliminato` reaches the Parlanti purge inside the deleting
  transaction (row counts on real SQLite, the `avvio` e2e pattern of ADR 0020/0024).
- Tests: delete a non-last `Parte` with a shared `Voce` (it survives with its name, loses this `Parte`'s print); delete
  the last `Parte` (every row of the `Incontro` gone, `incontro` row gone); a missing Sintesi or Trascrizione subscriber
  fails the last-`Parte` delete on the immediate FKs; a missing Parlanti purge fails the COMMIT on the deferred FKs.

## Amendment 2026-10-03 — §2 order inside Trascrizione's subscriber, as built [I2 amendment pass, incontro D-0049]
Confirmed, and stated exactly: `ApplicaEliminazioneRegistrazionePolitica` (1) vetoes on an open `Elaborazione` of this
`Parte`; (2) removes its `Elaborazione`s **first**; (3) `rimuoviParte` on the root, if the `Parte` has a `Trascritto`;
(4) persists the root — `rimuovi(incontroId)` when `incontroCessato`, else `salva(root)`; (5) **then** publishes
`TrascrittoEliminato` for the Parlanti purge. The root's rows go before the purge runs: the `voce_incontro` / `voce`
references from Parlanti are deferred FKs, so the order is safe and checked at COMMIT (step 6); Parlanti reads only the
event payload (`vociRimosse`). "After `rimuoviParte`" in §2 means step 4 above.
