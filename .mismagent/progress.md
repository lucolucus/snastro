# Progress

## 01-elimina-progetto-dalla-lista — 2026-10-08
- `SessioneProgetto.elimina(percorso)` (impl in `:avvio`: `cancellaCartella` walks bottom-up without following links, then `registro.rimuovi` on the registry's own executor, waited for); S1 card "⋯" → "Elimina…" → `ConfermaEliminaProgetto` over `PannelloSuVelo` (extracted from the new-project panel).
- Rule EX-2 in one place: `ConfermaEliminazione.confermataDa(nomeDigitato)`; typed name is local UI state, the presenter re-checks it; a failed delete stays inside the confirmation (`errore`).
- detekt `TooManyFunctions` (threshold 11, defaults: `config/detekt/detekt.yml` sets none) bit `ProgettiPresenter` at 13; fixed without suppressions (`aggiorna` became a file-level extension). `:ui` `renderCheck` (tag `render`) runs only in `check`, not in `:ui:test`.
- Acceptance tests live in `avvio/src/test/kotlin/snastro/avvio/accettazione/` (proposal in `conventions-proposals.md`), presenter over the real `SessioneProgettoImpl` with `Dispatchers.Unconfined`.
- Next slices: 02 refines `cancellaCartella` to snastro-only content + partial outcome; 03/04 add the `.lock` check — `elimina` takes no lock yet.

## 01-elimina-progetto-dalla-lista — 2026-10-08
- Rework B1: the "⋯" button + menu is now one shared `snastro.ui.stile.MenuAltreAzioni(tag, abilitato, contenuto)` (focus returns to the button on close, L742b); used by the project card and the registrazioni rows.
- Parlanti and pannello voci still hand-write it (design note); a later slice touching them should switch to the shared one.

## 06-test-accettazione-in-tests-acceptance — 2026-10-08
- `EliminaProgettoDallaListaTest` (EX-1..3) moved to `tests/acceptance/src/test/kotlin/snastro/avvio/accettazione/`, compiled as a test srcDir of `:avvio`, and added to `:avvio`'s plain `detekt` source (`avvio/build.gradle.kts`); `mm gate` green, it finds the markers.
- Presenter on `StandardTestDispatcher` + `runTest(dispatcher)` + `advanceUntilIdle()` (`@OptIn(ExperimentalCoroutinesApi::class)` as in `CodaCondivisaMultiSorgenteTest`); the real session keeps its own `CoroutineScope(SupervisorJob())`; both cancelled in `@AfterEach`.
- detekt really scans the new dir: a planted long line was reported at `tests/acceptance/.../EliminaProgettoDallaListaTest.kt:133:1 [MaximumLineLength]`.
- `mm check` fails when a conventions file cites a missing path: the skill's "Shown by" path was updated with the move.
- New acceptance tests for other screens go in the same dir (another package needs its own srcDir only if outside `:avvio`).

## 02-elimina-solo-contenuti-snastro — 2026-10-08
- `SessioneProgetto.elimina` now returns `Esito<ProgettoEliminato>` (`Completamente` / `RestanoFileUtente(cartella)` / `InParte(cartella)`); the impl refuses a folder without `progetto.db` (`CartellaNonValida`, nothing touched), removes the registry entry FIRST (a failing registry leaves the folder whole), then `cancellaContenutiSnastro` deletes only `progetto.db{,-wal,-shm}`, `audio/`, `cache/`, `sbobinature/`, `.lock`, without following links, going on after a failure.
- S1 shows what is left in a dismissible `BannerSn` (`Dati.avvisoEliminazione`, tag `progetti-avviso-eliminazione`); the folder is written `~/…` by `percorsoDaCasa` (`TestiProgetti.kt`), the home injected by `:avvio` (`cartellaUtenteReale()`); proposal in `conventions-proposals.md`.
- EX-10's undeletable file is real in the acceptance test (`audio/` loses its write permission, as `ProvisioningModelliTest:331`); it would not fail as root.
- `ProgettiPresenter` is at detekt's `TooManyFunctions` limit (10, threshold 11 → reported at 11, seen in `./gradlew check`): the next S1 action needs a split (design note).
- Slices 03/04 (the `.lock` check) plug in before `registro.rimuovi` in `SessioneProgettoImpl.elimina`.

## 02-elimina-solo-contenuti-snastro — 2026-10-08
- Rework 1: `EliminaSoloContenutiSnastroTest` builds the real session inside `conScopeDiProva` (helper `conSessione`, session closed and scope cancelled in `finally`); no implicit-Default scope left in the file.
- Learned: `backgroundScope` does not fit a presenter driven by `advanceUntilIdle()` — that call ignores background tasks (the test stayed in `Caricamento` when I tried it); keep `CoroutineScope(SupervisorJob() + dispatcher)` cancelled in `@AfterEach` for the presenter.
- `EliminaProgettoDallaListaTest` and `SessioneProgettoImplEliminaTest` still use `CoroutineScope(SupervisorJob())` (design note / review advice), not touched in this rework.

## 07-testo-conferma-e-cartella-utente — 2026-10-08
- `MESSAGGIO_CONFERMA_ELIMINA_PROGETTO` now promises only snastro's contents ("…Gli altri file nella cartella restano."), pinned by `TestiProgettiTest` (EX-9).
- `ProgettiPresenter(…, cartellaUtente: String)` has no default; `EliminaProgettoDallaListaTest` (EX-1..3) passes `cartellaUtente = ""`. The private test builder `presentatore(…)` in `ProgettiPresenterTest` keeps `cartellaUtente = ""` as a fixture default and passes it explicitly to the constructor.
- Seen once: `./gradlew check` failed with `adr-0034-tabelle-incontro-confinate: timed out after 60s` (`ControlliAdrTest.kt:157`) during a 10m41s run; `./gradlew :architettura-test:test --rerun-tasks` passed right after, and `mm gate` was green. Treat it as a load-dependent timeout, not a defect (assumption).
