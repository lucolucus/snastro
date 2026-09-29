---
scope: global
status: accepted
supersedes: null   # partial, amended in place with dated pointers here: ADR 0021 §10 (the release composition R3 → the single composition), ADR 0024 §4 (release split; subscriber order declared — closes pending user decision 5), ADR 0020 (R2 wording, by pointer from 0024), architecture.md (:avvio row, "Composition R3" / R1-read-model amendment lines, new § Composition), dev-architecture-app.md (§10 composition)
closes_spike: null
decided: 2026-09-27 · user (post-R3 design review, analysis §2.3 R1–R4, §2.4 X4, §6.1; C1–C5 as recommended; the release METHOD is unchanged) · architect (target shape, AC table, block split)
enforced_by:
  - check: architettura-test/controlli-adr/adr-0030-composizione-unica.sh
    from: c3-composizione-piatta
    # FAIL if (1) a directory avvio/src/main/kotlin/snastro/avvio/r<digit> exists; (2) avvio/src/main has a cast
    # `as Collaboratori…` / `as? Collaboratori…`; (3) avvio/src/main has `AtomicReference<CodaCondivisa`; (4) a file
    # under avvio/src/main/kotlin/snastro/avvio/ outside `<ctx>/` and `progetto/` imports or fully-qualifies
    # `snastro.<ctx>.adattatori` (ctx ∈ progetto|trascrizione|parlanti|documento|sintesi; comment lines stripped).
    # Clause (4) replaces GrafoR0Test's AC-350 package guard. Red-green fixtures in
    # controlli-adr/fixture/adr-0030-composizione-unica/, validated via ControlliAdrTest.
---
# 0030 — Single composition, one module per context; R0–R2 retired as code (the release method stays)

## Context
`:avvio`'s composition root was layered by release (R0 → R1 → R2 → R3) so that each functional release could ship
on its own (ADR 0021 §10; project memory "build by functional releases"). R3 is now the only app shipped, and the
layering costs more than it returns (analysis §2.3):
- **Duplicated shells.** Three near-identical `ContenutoApp*`, plus R0's in `Main`.
- **Dead or test-only code.** `costruisciGrafoR1/R2` are dead, and `ContenutoAppR1/R2` exist only for tests.
- **Casts and a workaround.** Chained downcasts (`r3.r2.r1.coda`, `as? CollaboratoriR3`), and an `AtomicReference`
  that works around the queue↔sintesi cycle.
- **Duplicated instances.** Adapters are built several times per project (`TrascrittoRepositorySql` five times), and
  there is a second `StatiElaborazione` fed by a fake `FasiInCorso`.
- **Hidden order.** The synchronous-subscriber order of AC-S143 is encoded in decorator nesting and in `init` side
  effects, and the test proves it by reflection.
- **UI flags.** Presenters carry 8–13 nullable parameters that act as release feature flags (U1).
- **Tests of absent features.** Tests prove that features are absent from releases nobody ships.

The user decided: the release **method** stays (build by visible functional releases, only HIGH gates merges). A
release no longer survives as a separate code composition.

## Decision

### 1. Target shape (`:avvio`)
- **`PorteProgetto`: built once per open project by `SessioneProgettoImpl`.** It holds:
  - the database, and **one** `UnitaDiLavoroSql`, used as both `UnitaDiLavoro` and `LetturaCoerente`
    ([ADR 0029](0029-lettura-coerente-deferred.md));
  - the `DispatcherEventiInMemoria` over it;
  - every SQL repository, **one instance each**;
  - `CatalogoRegistrazioni`, the cross-context readers, and `LayoutCartellaProgetto`.
  - Nothing else builds a repository.
- **`ModuloComposizione<Ctx>`, one per context**: `ModuloTrascrizione`, `ModuloParlanti`, `ModuloSintesi`,
  `ModuloDocumento`, plus Progetto's part in `avvio.progetto`. Each one is built from `PorteProgetto` and exposes:
  - `abbonatiSincroni(): List<Abbonamento>` and `abbonatiDopoCommit(): List<Abbonamento>`. These are **values**:
    each pairs an event type with an `AbbonatoSincrono`/`AbbonatoDopoCommit` from the adapters [user C3]. No
    adapter registers itself in `init`, and adapters stop importing the concrete `DispatcherEventiInMemoria` (X4);
  - `fontiCoda(): List<FonteCoda>`;
  - `avvia(scope)` for its background loops (e.g. the `RitentaConBackoff` workers of [ADR 0028](0028-librerie-tecniche-supporto.md)),
    and `ferma()`;
  - its typed collaborators for `:ui`.
- **`apriProgetto(porte)`: the only place with order**, top to bottom:
  1. build the modules;
  2. register the synchronous subscribers from ONE declared list (§2);
  3. register the after-commit subscribers;
  4. build `CodaCondivisa(fonti of every module)`. A `Campanello` wake-up handle is created before the modules
     and handed to the ones that enqueue, which breaks the queue↔sintesi cycle with no `AtomicReference`;
  5. run the startup recoveries (AC-S145: both before the first claim);
  6. `avvia(scope)` on every module and on the queue.
- **Shutdown.** One `ArrestoProgetto(scadenza)` stops everything in reverse order, with one shared deadline
  (ADR 0017 §3 unchanged in substance).
- **`CollaboratoriProgetto(trascrizione, parlanti, sintesi, documento)`.** The fields are non-null and typed, with
  no casts and no `r3.r2.r1`.
- **Singletons.** One `Grafo` (R0's `GrafoR0` flattened into it), one `ContenutoApp`, one `SEZIONI_SHELL`, one `io`
  dispatcher, one merge of `AggiornamentiVista`.
- **Packages, by concern** [user C5]: `snastro.avvio.progetto`, `.trascrizione`, `.parlanti`, `.sintesi`,
  `.documento`, `.modelli`, `.coda`, `.smoke`. `Main.kt` stays in `snastro.avvio`.
  - Material that sits in a "release" package today moves to its concern: `ErroreApplicazioneAvvio`,
    `ServizioModelliProvisioning`, `SelezioneAdattatoriMl`, `SchermataR1`, …
  - The ADR 0019 §4.1 glue (`LavoriPerChiave`, `ProposteSerializzate`, `AzioniSomiglianzaProgetto`) stays in
    `:avvio`, in `avvio.parlanti`.
- **Retired:**
  - `costruisciGrafoR1/R2`, `GrafoR1/R2/R3`;
  - `ContenutoAppR1/R2/R3` and R0's `ContenutoApp`;
  - `EstensioneSessione`'s release chaining, `EstensioneR1/R2/R3`, `CollaboratoriR1/R2/R3`;
  - `LettoreNomiVuoto`, `SEZIONI_SHELL_R0/R1`.
- **Presenters.** Once the flat composition lands, their optional collaborators become mandatory (U1). A missed
  wiring then fails to compile instead of hiding a function.

### 2. Synchronous-subscriber order: declared, unchanged [user C2 — closes pending decision 5]
The list in `apriProgetto` is **Sintesi → Parlanti → Trascrizione**, exactly today's effective order (Sintesi's
`TrascrittoSostituito` and `RegistrazioneEliminata` subscribers first, then Parlanti's purges, then Trascrizione's
veto/purge).
- **Correctness does not depend on it.** Every synchronous subscriber runs in the command's single transaction, and
  a veto or failure in any position dooms the whole unit (ADR 0012, ADR 0020 §2).
- **Why declare it anyway:** so it is reproducible and readable. A reorder is a one-line diff reviewed against this
  ADR.
- AC-S143 asserts the declared list directly.
- ADR 0024 §4 carries the dated note.

### 3. The ACs asserted on R0/R1/R2 graphs [user C4]
| AC (current test) | Fate |
|---|---|
| AC-350 "la shell R0 non include la sezione Parlanti" (`GrafoR0Test`) | **deleted**: R0 is not shipped |
| AC-350 "fuori dai pacchetti r1, r2 e r3 … non importa i contesti delle release successive" (`GrafoR0Test`) | **deleted**, replaced by this ADR's `enforced_by` clause (4): context adapters only in their composition package |
| AC-350 "in modo R0 … non costruisce alcuna sorgente di Trascrizione" (`GrafoR0Test`) | **deleted** |
| AC-350 "RegistrazioniPresenter costruito da R0 non porta mai uno stato di elaborazione" (`GrafoR0Test`) | **deleted** |
| AC-350 "dopo AggiungiRegistrazione nessuna riga esiste in elaborazione" (`AggiungiRegistrazioneR0Test`, `modelli`) | **merged into AC-371** |
| AC-371 "dopo un import nessuna Elaborazione esiste, la vista da NON_AVVIATA e S2 mostra Trascrivi" | **retargeted** to the single composition |
| AC-355 "nessun abbonato sincrono e registrato, in particolare su RegistrazioneAggiunta" | **retargeted**: "no synchronous subscriber on `RegistrazioneAggiunta`" (ADR 0012 (c)), asserted on the declared lists |
| AC-355 "nessun servizio … e costruito con la UnitaDiLavoroSql grezza" | **retargeted**: every command service gets the dispatcher's unit of work; `LetturaCoerente` is the same instance (ADR 0029 §3) |
| AC-356 "nessuna classe di parlanti e referenziata dalla composizione R1" | **deleted** |
| AC-356 "senza Parlanti il Documento rende ogni Voce come Voce n e una Revisione committata lo rigenera" | **retargeted**: "a Voce with no named Parlante renders as `Voce n`; a committed Revisione regenerates the Documento" |
| AC-S143 "i cinque abbonati sincroni sono registrati prima della coda e di qualunque comando" | **rewritten**: asserts the declared list and that registration precedes the queue and any command. No reflection on `sincroni`, no line-index scan of `EstensioneR3.kt` |
| AC-S143 "la composizione R2 non mostra la scheda Riassunto e non crea righe riassunto" | **deleted** |
| AC-S119 ×2 in `:ui` ("senza lo slot Riassunto…", "senza SorgenteRiassuntoS3…") | **deleted with U1** (block `c4`), when the slot becomes mandatory |

- Every other AC under `avvio/src/test/.../r1|r2|r3` **keeps its id**. It moves to the per-concern test package and
  runs on ONE test `AmbienteProgetto`, built through the production `apriProgetto` (never re-wired by hand).
- Earlier features' manifests are not edited. This table is the record of the retirement.

### 4. Blocks (each a PR, gate green)
- `c1-porte-progetto`: `PorteProgetto` built once, inside the current R-structure. Low risk. It removes the duplicate
  instances and the fake `FasiInCorso`.
- `c2-contenuto-app-base`: one shared `ContenutoApp` body (R1 of the analysis). R0–R2 still exist but share it.
- `c3-composizione-piatta`:
  - the modules, `apriProgetto`, the declared lists, subscribers as values in the adapters (X4), the `Campanello`;
  - typed `CollaboratoriProgetto`, the packages by concern;
  - the retirement of R0–R2, the AC table of §3, `ArrestoProgetto`;
  - the script of this ADR.
  - It may be delivered as two PRs (subscribers-as-values first, then the flat root), but it is one block.
- `c4-presenter-obbligatori`: mandatory presenter collaborators (U1), the `:ui` AC-S119 deletions, and the single
  test Ambiente.

## Rejected options
- **Deduplicate only (keep R0–R2).** Cheaper, but the casts, the implicit order and the absence tests remain.
- **One composition with a `Rilascio` selector.** It keeps "R2 mode" testable, but the nullable feature flags survive
  in every presenter.

## Consequences
- **A future release** adds a `ModuloComposizione` to the ordered lists plus its ACs. Unfinished work lives on a
  branch, never in a parallel composition.
- **ADR 0021 §10:** "composition R3" now reads "the Sintesi module of the single composition".
- **ADR 0024 §4:** "an R2 composition" no longer exists; the order note is recorded there.
- **`architecture.md`:** a new § Composition; the `:avvio` row is updated.
- **`dev-architecture-app.md` §10** is the composition how-to.
- **`adr-0023-posizione-in-coda-fuori-dai-contesti.sh`** must stay green, because positions stay in `:avvio`
  (`avvio.coda`).
- **`--smoke`** stays in `main()` (`avvio.smoke`). Moving its Finte out of testFixtures (M2) is a later, separate
  decision.
