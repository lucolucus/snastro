VERDICT: REWORK
SHA: 6ac4aac2621f181aa534ef9d841649b36b7b4445
SCORES: simple=4 naming=4 modularity=4 duplication=4 concision=4 errors=4 tests=4

# Review — 02-elimina-solo-contenuti-snastro (Base 6049d504)

Gate: `mm gate` green (`./gradlew check`); `mm check --base 6049d504` ok (ADR "no Evidence" warnings only, pre-existing).
Test results at HEAD: `EliminaSoloContenutiSnastroTest` 2/2, `SessioneProgettoImplEliminaTest` 3/3, `TestiProgettiTest` 3/3.

| dimension | score | evidence |
|---|---|---|
| Simple design | 4 | `CancellazioneCartella.kt:27-40` one list, one tree delete, one outcome; `SessioneProgettoImpl.kt:199-204` validate → registry → delete, in that order. Dodge in `ProgettiPresenter.kt:173-175` (see advice). |
| Naming | 4 | `ProgettoEliminato.Completamente/RestanoFileUtente/InParte` (`ui/.../ProgettoEliminato.kt`), `percorsoDaCasa`, `cancellaContenutiSnastro`; file still named `CancellazioneCartella.kt`. |
| Modularity | 4 | outcome type next to its port `SessioneProgetto` in `:ui` (as `ErroreSessione`); `:ui` never reads `user.home`, `:avvio` injects it (`Grafo.kt:65`, `Presenter.kt:39`). |
| Duplication | 4 | `user.home` read once (`Grafo.kt:65`); `~` formatting once (`TestiProgetti.kt:59-62`). |
| Concision | 4 | `cartellaUtente: String = ""` (`ProgettiPresenter.kt:36`) exists for one out-of-slice test (design note logged). |
| Error handling | 4 | every tree failure logged and turned into `InParte`, the walk goes on (`CancellazioneCartella.kt:59-78`); a non-project folder is a typed `CartellaNonValida` with nothing touched (`SessioneProgettoImpl.kt:201`). |
| Tests | 4 | EX-9/EX-10 proven at the presenter seam over the real session and real files, exact messages; symlink, non-project folder and failing registry covered at the unit seam. |

## Safety of the deletion (checked)
- Only the seven constant names under the folder are ever resolved (`CancellazioneCartella.kt:20`): no input-derived path, no `..`.
- A folder without a readable regular `progetto.db` is refused before anything (registry included) is touched — `SessioneProgettoImpl.kt:201`, test `una cartella che non e un progetto snastro…`.
- A symlink among the seven names (e.g. `audio` → elsewhere) is deleted itself: `cancellaAlbero` checks existence with `NOFOLLOW_LINKS` and `walkFileTree` without `FOLLOW_LINKS` visits a symlinked root as a file — test `una cartella di snastro che e un link simbolico…`; a symlink deeper inside (`audio/collegamento.wav`) is deleted, its target kept — EX-9 test line 455.
- The folder itself is removed only with `Files.delete` (never recursive), so the user's files make it fail with `DirectoryNotEmptyException` → `RestanoFileUtente`.
- Registry first: a failing `rimuovi` leaves the folder whole (test `se il registro non toglie la voce…`).

## Blocking
1. **Conventions skill, topic acceptance-tests ("Wiring and scheduling", decided at Base 6049d504): "do not generalize an implicit `Dispatchers.Default`; use `conScopeDiProva` / `backgroundScope` for test-created scopes".**
   `tests/acceptance/src/test/kotlin/snastro/avvio/accettazione/EliminaSoloContenutiSnastroTest.kt:56` builds the
   session's scope as `CoroutineScope(SupervisorJob())` — the implicit-Default pattern the topic forbids, copied from
   `EliminaProgettoDallaListaTest.kt:53` (already a design note). This is a second way of doing what the skill decides.
   Fix: create the session scope through `snastro.supporto.test.conScopeDiProva` (explicit Default, cancelled in
   `finally`) — e.g. wrap each test body / build the session inside it — or, if the session closes cleanly under the
   test scheduler, `backgroundScope` of `runTest`; update the KDoc ("the session keeps its own real scope") accordingly.

## Advice (not blocking)
- `ProgettiPresenter.kt:173-175`: `chiudiAvvisoEliminazione` is a lambda, unlike its siblings `chiudiErroreCrea/Apri`
  (`:159-162`), only so that detekt's `TooManyFunctions` does not count it — the comment says so. It is a lint dodge in
  all but name; the split already noted in design-notes is the real fix, better done before the next S1 action.
- Folder that is itself a symlink to a directory: `validaCartellaProgetto` follows it (`Files.isDirectory`), the seven
  entries are deleted in the target, then `Files.delete(cartella)` removes the link and reports `Completamente` — even if
  the target still holds the user's files. No data is lost, but the message would be wrong; checking
  `Files.isSymbolicLink(cartella)` (refuse, or report `RestanoFileUtente` with the target) would close it.
- `documenti/` (pre-ADR-0031 name, `MigrazioneCartellaSbobinature.kt:10`) is snastro-created but not in the list: a
  project where the migration did not happen keeps it and gets the "file non creati da snastro" message. Safe side;
  EX-9 does not list it.
- `ProgettiPresenterTest` EX-9 test (`:226-247`) computes its expected text with the production `avvisoEliminazione`, so it
  does not prove the `~` its name promises (the acceptance test does); asserting the literal would make it a spec.
- `SessioneProgettoImplEliminaTest.kt:39` uses the same implicit-Default scope (unit test, outside the topic's letter;
  `conScopeDiProva` would align it).

## Proposals
`conventions-proposals.md` "display-paths" matches the diff: one pure `percorsoDaCasa` (`TestiProgetti.kt:59`), home
injected by `:avvio` (`cartellaUtenteReale()`, `Grafo.kt:65`, `Presenter.kt:39`), tests in `TestiProgettiTest.kt`.

## Other examples' tests
Not edited: `EliminaProgettoDallaListaTest.kt` untouched; `ShellPresenterTest`, `ProgettiPresenterTest`,
`SessioneProgettoFinta` changed only for the new return type.
