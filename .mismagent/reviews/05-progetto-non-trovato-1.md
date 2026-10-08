VERDICT: PASS
SHA: 4564efb13ae8375d37388cabd1bdcee0a643307f
SCORES: simple=4 naming=4 modularity=4 duplication=4 concision=4 errors=4 tests=4

# Review — 05-progetto-non-trovato (Base fe719656)

Gate: `./gradlew check --rerun-tasks` BUILD SUCCESSFUL (every task re-run, so the touched tests really ran);
`mm gate` green (test, lint, check); `mm check --base fe719656` green (only the pre-existing ADR "no Evidence"
warnings). Fresh results: ProgettoNonTrovatoTest 4/4, SchermataProgettiNonTrovatoTest 3/3,
SessioneProgettoImplTest 29/29, CartelleProgettiFileTest 3/3, CartelleProgettiFintaTest 2/2, ElencoProgettiTest 5/5,
ProgettiPresenterTest 18/18, ShellPresenterTest 18/18, TestiProgettiTest 6/6, EliminaProgettoDallaListaTest 3/3.
No new `@Suppress`, no baseline change.

## Examples

| EX | acceptance test | verdict |
|---|---|---|
| EX-7 | `ProgettoNonTrovatoTest.kt:61` — real folder moved to `~/Desktop`, fresh list, `cartellaTrovata == false`; the "Cartella non trovata" mark, "Togli dalla lista" present and "Elimina" absent are in the paired Compose test `SchermataProgettiNonTrovatoTest.kt:29-47` | proven (split presenter test + thin Compose test, as the acceptance-tests convention prescribes) |
| EX-8 | `ProgettoNonTrovatoTest.kt:72` — list empty, registry empty, recursive content of `~/Desktop/test.snastro` unchanged | proven |
| EX-12 | `ProgettoNonTrovatoTest.kt:88` — not found, folder put back, a fresh list shows it found, it opens (`corrente.percorso`), then deletes through confirm-by-typing and leaves the list | proven |
| EX-13 | `ProgettoNonTrovatoTest.kt:112` — exact text "Cartella non trovata: ~/Documents/snastro/test.snastro", nothing opened, still listed and still in the registry | proven |

Tests of other examples: the four earlier acceptance tests only swap `ElencoProgetti(registro)` for
`elencoProgetti(registro)` (the `:avvio` binding with the real folder check); their givens, whens and thens are
untouched.

### The CartellaNonValida → CartellaNonTrovata change (asked by the dispatcher)
Not a weakening:
- AC-349's text (`avvio-r0.md:60`) is "una cartella senza progetto.db o non leggibile → CartellaNonValida"; a path
  that does not exist is not in it. Its own test `AC-349 aprire una cartella senza progetto db restituisce
  CartellaNonValida` (`SessioneProgettoImplTest.kt:270`) is unchanged and green, as are the other three AC-349 tests
  and the H3 lock-release tests. The rewritten test (`SessioneProgettoImplTest.kt:279`) asserts a *stronger* value
  (the error carries the exact path).
- `SessioneProgettoContratto.kt:57-80` (elimina then apri): still an exact typed error, now the one that is true —
  the folder was removed entirely. Both the Finta and the real impl satisfy it.
- `ShellPresenterTest.kt:154,191` (AC-181 and the open-project variant): still assert the mapped message and the state
  shape; only the expected error type follows the Finta.
- `crea` (`SessioneProgettoImpl.kt:124`), the db-open failures (`:172`, `:184`) and `elimina` of a non-project folder
  (`:211`) still answer CartellaNonValida — EX-1/EX-6 "only a snastro folder is touched" is unaffected.

## Scores

| dimension | score | evidence |
|---|---|---|
| Simple design | 4 | `FlussoTogliDallaLista.kt:19-30` one flow on the shared runner; `ElencoProgetti.kt:14-23` one extra field; `elimina` reuses `togliDallaLista` (`SessioneProgettoImpl.kt:233,245`). |
| Naming | 4 | domain words throughout: `CartelleProgetti.esiste`, `cartellaTrovata`, `togliDallaLista`, `CartellaNonTrovata(cartella)`; texts in `snastro.ui.testi` (`TestiProgetti.kt:57-75`). |
| Modularity | 4 | port in `:progetto:applicazione` (`porte/CartelleProgetti.kt`), file adapter in `:progetto:adattatori` (`CartelleProgettiFile.kt`), bound once in `:avvio` (`ModuloProgetto.kt:172-174`, `Grafo.kt:105`); acceptance tests reach it through the binding, not the adapter. Lock helpers moved to a cohesive `LockCartella.kt` to stay under detekt's file limit. Architecture-map amendment logged by the builder in design-notes. |
| Duplication | 4 | one runner (`CorsaSingola`), one text function for the not-found message (`avvisoCartellaNonTrovata`, used by both `messaggioPer` overloads). Minor: "folder exists" is `Files.isDirectory` in both `CartelleProgettiFile.kt:9` and `SessioneProgettoImpl.kt:149` (and again inside `validaCartellaProgetto`, `:415`). |
| Concision | 4 | nothing unused; `CartelleProgettiFinta` and contract are used by both test suites. |
| Error handling | 4 | typed `ErroreSessione.CartellaNonTrovata` for the expected case; a registry fault in `togliDallaLista` throws to `CorsaSingola`, which shows the one generic message (tested, `ProgettiPresenterTest` "togliere dalla lista che fallisce…"); dismissible `erroreTogli`. Nothing on disk is touched by "Togli dalla lista" (EX-8 compares recursive content). |
| Test quality | 4 | one behaviour each, named as the behaviour; port contract run against Finta and real FS (`CartelleProgettiContratto.kt`); acceptance at the presenter seam over real `SessioneProgettoImpl` and real folders. |

## Conventions
- acceptance-tests: followed (`conSessione`, `StandardTestDispatcher`, scope cancelled in `@AfterEach`, KDoc names the
  substituted boundaries, paired thin Compose test).
- presenter-split: `FlussoTogliDallaLista` is an `internal` holder on the shared `CorsaSingola`, bound directly in
  `azioni` (`ProgettiPresenter.kt:103-104`).
- display-paths: EX-13 reuses `percorsoDaCasa` as the topic requires; the first error that carries a path, and the
  two-argument `messaggioPer(errore, cartellaUtente)`, are proposed in `.mismagent/conventions-proposals.md` and the
  proposal matches the diff (`TestiProgetti.kt:71`, `CorsaSingola.kt:53`, `ShellPresenter.kt:117`).

## Findings

No blocking findings.

Advice (non-blocking):
1. `ShellPresenter.kt:117` now writes the folder from the home, but every `ShellPresenterTest` builds the shell with
   `cartellaUtente = ""` (`ShellPresenterTest.kt:118,313`), so the `~` path of the shell is untested; one test with a
   home would pin it.
2. `SessioneProgettoImpl.kt:149` checks `Files.isDirectory` and `validaCartellaProgetto` (`:415`) checks it again; a
   regular file at the path is now reported as "Cartella non trovata" rather than "non valida" — acceptable, but
   worth a word in the KDoc of `apri`.
3. `SessioneProgettoContratto.kt:83` "un progetto tolto dalla lista non e cancellato e si riapre" passes trivially on
   `SessioneProgettoFinta` (its `togliDallaLista` is a no-op, `SessioneProgettoFinta.kt:44`); the drop from the list
   itself is proven only by the acceptance test. Fine for now; the contract name promises a little more than it checks.
