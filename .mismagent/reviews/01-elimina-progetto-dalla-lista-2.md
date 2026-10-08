VERDICT: PASS
SHA: ebf011c47c8ad1fbe47c047bc41a7c5262fccfbb
SCORES: simple=4 naming=5 modularity=4 duplication=4 concision=4 errors=4 tests=4

# Review 2 — 01-elimina-progetto-dalla-lista (slice, base 22db6f14)

Gate: `mm gate` green (`check: green`, `gate: green`; only pre-existing ADR "no Evidence" warnings);
`mm check --base 22db6f14` ok. Targeted run of `EliminaProgettoDallaListaTest`, `SchermataProgettiEliminaTest`
and the `SchermataRegistrazioni*` tests green. No new suppression: `@Suppress`/`detekt:disable` lines are 236 at
base and at HEAD; the only touched one lost an entry (`PannelloNuovoProgetto.kt:57`, `"LongMethod"` dropped).

Rework scope reviewed: `git diff 3a6559c7..HEAD` (commits a990a2b2, ebf011c4), plus the full slice diff for the
re-scored dimensions.

| dimension | score | evidence |
|---|---|---|
| Simple design | 4 | `EliminaProgetto.kt:35-47` `MenuProgetto` now only says which item the card offers; button/menu/focus live in `stile/MenuAltreAzioni.kt:19-41` |
| Naming | 5 | `MenuAltreAzioni(tag, abilitato, contenuto)` with `chiudi` slot; `ConfermaEliminazione.confermataDa`, `chiediElimina/annullaElimina/confermaElimina` unchanged |
| Modularity | 4 | shared component sits in `snastro.ui.stile` beside `BottoneIconaSn`, `CampoSn` etc.; `internal` to `:ui`; port/impl split unchanged (`SessioneProgetto.elimina` in `:ui`, `SessioneProgettoImpl` in `:avvio`) |
| Duplication | 4 | B1 resolved: the private copy in `SchermataRegistrazioni.kt` (old 750-774) removed, its two call sites (`:532`, `:720`) and `EliminaProgetto.kt:37` use the one `stile/MenuAltreAzioni.kt`; the remaining hand-written copies (parlanti, pannello voci) are outside the diff and correctly kept in `design-notes.md` line 3 |
| Concision | 4 | moved code is verbatim, unused imports removed in both files (`DropdownMenu`, `Box`, `BottoneIconaSn`, `Icona`, `ETICHETTA_ALTRE_AZIONI`, `ColumnScope`) |
| Errors | 4 | unchanged since review 1: I/O faults caught once at `avvia`'s boundary into `MESSAGGIO_ERRORE_GENERICO` inside the confirmation, tested in `ProgettiPresenterTest` |
| Tests | 4 | unchanged since review 1: one behaviour each, named after the example, at the presenter seam over the real `SessioneProgettoImpl` and real folders |

## Examples

- **EX-1**, **EX-2**, **EX-3** — `avvio/src/test/kotlin/snastro/avvio/accettazione/EliminaProgettoDallaListaTest.kt`
  unchanged by the rework; same given/when/then as `.mismagent/examples.md` rows 7-9; green. The menu's test tag
  (`progetti-altre-azioni-$id`) and item tag (`progetti-menu-elimina-$id`) are preserved, so
  `SchermataProgettiEliminaTest` still drives the real view and passes.
- No test of another example was edited by the rework.

## Blocking

None. B1 from review 1 is fixed exactly as asked: one shared `MenuAltreAzioni`, focus returns to the "⋯" button
on close (L742b, `MenuAltreAzioni.kt:25-30`), and the project card's copy is gone from the design note.

## Advice (not blocking)

- `EliminaProgetto.kt:40-43`: `chiudi()` requests focus on the "⋯" button and then the confirmation opens; same
  order as the registrazioni rows, so consistent — just make sure the confirmation's name field takes focus when
  it appears (if not already), otherwise keyboard users land back on the button.
- Carried over from review 1 for slice 02: `SessioneProgettoImpl.elimina` deletes recursively without checking the
  path is a snastro project folder (EX-9 should add the guard), and deletes the folder before the registry entry
  (design note 4).

## Conventions proposals

Unchanged; both proposals (acceptance-test location; confirm-by-typing) still match the diff. Moving an existing
component into `snastro.ui.stile` follows the established home of shared components and needs no proposal.
