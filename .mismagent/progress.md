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

## 03-elimina-con-lock-e-coda — 2026-10-08
- `SessioneProgettoImpl.elimina` takes the project's `.lock` (same `acquisisciLock` as `apri`) before touching anything and holds it through the registry removal and the deletion (`eliminaSeNonAperto`); held by another instance → new `ErroreSessione.ProgettoApertoAltrove` ("Questo progetto è aperto in un'altra finestra.", no "già": `ProgettoGiaAperto` stays the `apri` text). The `.lock` file is deleted while held, then released.
- EX-11 was already green before any change (elimina never opens the DB, so a queued `in_attesa` riassunto goes with the folder); its acceptance test seeds a real `incontro` + `riassunto` through `IncontroRepositorySql`/`RiassuntoRepositorySql`.
- EX-6's other window is a second `SessioneProgettoImpl` in the test JVM that `apri`s the project; asserting on an open project's folder must skip `progetto.db-wal/-shm` (a `Files.walk` failed with `NoSuchFileException …/progetto.db-shm`).
- Slice 04 (elimina of the project open in THIS instance): today `elimina` on it returns `ProgettoApertoAltrove` too (the same-JVM `OverlappingFileLockException` path), so the open-project flow must `chiudi` first, then `elimina`.

## 08-presenter-progetti-flusso-eliminazione — 2026-10-08
- `ProgettiPresenter` split: `CorsaSingola` (internal, over the shared `MutableStateFlow`: `dati`, `aggiorna`, single-flight `avvia`) and `FlussoEliminazione` (internal: `chiedi`/`annulla`/`conferma`/`chiudiAvviso`); `azioni` wires the latter directly, no forwarding members — tests call `presenter.azioni.chiediElimina/confermaElimina/annullaElimina`. Presenter now has 6 functions (threshold 11).
- `CorsaSingola.avvia` catches through `catturaNonFatale` (CR-19b, `RegoleArchitetturaliTest` frozen counts: a new file with a raw catch-all fails `CR-19b ... congelato 0`); `ProgettiPresenter`'s frozen count lowered 2 → 1. Side effect: a non-VM `Error` thrown by an operation now lands as the generic message instead of escaping (same policy as `catturaNonFatale` elsewhere).
- Commit 561e95ec was red on CR-19b (I misread a piped `mm gate | tail` exit code); fixed by the next commit, `mm gate` green at HEAD.
- Next S1 action (slice 05 "togli dalla lista"): add its own holder or a function on `FlussoEliminazione`-style class, reuse `CorsaSingola`; proposal `create presenter-split` in `conventions-proposals.md`.

## 09-test-accettazione-scope-e-contenuto — 2026-10-08
- One session builder: `conSessioneDiProva(registro) { sessione -> }` (`avvio/src/test/kotlin/snastro/avvio/progetto/SessioneDiProva.kt`, conScopeDiProva scope, `chiudi` in `finally`) and, over it, `conSessione(registro, dispatcher) { }` in the acceptance package (`ConSessione.kt`, adds `runTest(dispatcher)`); all three acceptance files and `SessioneProgettoImplEliminaTest` use them, no implicit-Default scope left there.
- EX-6's "niente cancellato" now compares recursive relative paths (`Files.walkFileTree`, skips `progetto.db-wal/-shm`, tolerates their `NoSuchFileException` in `visitFileFailed`).
- `SessioneProgettoImpl.chiudi` returns on a conScopeDiProva scope: acceptance + `SessioneProgettoImplEliminaTest` green 3/3 with `./gradlew :avvio:test --rerun --tests …` (exit 0 each).
- Still on an implicit scope: `SessioneProgettoImplTest`, `SessioneProgettoImplChiudiTest`, `RinominaRegistrazioneTest` (design note).

## 10-messaggio-inline-errore-condiviso — 2026-10-08
- `MessaggioInlineErrore(messaggio, onChiudi, tag)` now lives once in `ui/src/main/kotlin/snastro/ui/stile/MessaggioInlineErrore.kt` (internal); the copies in `PannelloNuovoProgetto.kt`, `SchermataRegistrazioni.kt`, `SchermataParlanti.kt` are gone, callers import `snastro.ui.stile.MessaggioInlineErrore`.
- Characterization: `ui/src/test/kotlin/snastro/ui/stile/MessaggioInlineErroreTest.kt` (text shown, `<tag>-chiudi` click calls `onChiudi`), green before and after the move; render checks unchanged.
- `registrazione/SchermataRegistrazione.kt` keeps its own private 2-arg `MessaggioInlineErrore` (different style/label); it does not import the shared one (explicit imports only), so no rename was needed.
- Detekt `ImportOrdering` is strict lexicographic (`MenuAltreAzioni` before `MessaggioInlineErrore`): seen red in `./gradlew :ui:detekt` on the first gate run.

## 11-menu-altre-azioni-parlanti — 2026-10-08
- `MenuAltreAzioniParlante` (`parlanti/SchermataParlanti.kt`) now calls the shared `snastro.ui.stile.MenuAltreAzioni` (tag `parlanti-altre-azioni-<id>`, `abilitato = !riga.operazioneInCorso`); its own button, `DropdownMenu`, `FocusRequester` and expanded state are gone, items call `chiudi` before their action.
- No new test: `ParlantiVistaTest` (More button, focus return) and `ParlantiRenderCheckTest` green unchanged; `mm gate` green (exit 0).
- All four "⋯" menus with focus return (progetti, registrazioni riga/incontro, parlanti) now go through `MenuAltreAzioni`; only `MenuAltreAzioniCarta` (registrazione) stays separate by design (design-notes.md).

## 04-elimina-progetto-aperto — 2026-10-08
- Header "⋯" → "Elimina progetto…" (`SchermataShell`, tag `shell-altre-azioni`/`shell-menu-elimina`) opens S1's same confirmation (`ConfermaEliminaProgetto`, now lambda-based; `ConfermaEliminazione(nome, percorso, errore)`), run by the shell holder `EliminazioneProgettoAperto` over `ShellUiStato.ConProgetto.conferma/eliminazioneInCorso`; `ShellPresenter.occupata()` is the one M3 guard.
- `SessioneProgettoImpl.elimina` of the project open here: `coda.inCorsoOInCoda()` (waiting read before running) → `ErroreSessione.ElaborazioneInCorso`; else `chiudiSessione` then delete, `corrente = null` only AFTER the registry removal, so the fresh S1 never lists it. `SessioneProgettoContratto` pins it for fake and real.
- Detekt limits hit: `SessioneProgettoImpl`/`CodaCondivisa` at TooManyFunctions 11 — `arresta` and `piccoGlobale` (ex `raccogliPicco`) moved to top-level private functions to make room; no suppression added.
- `AmbienteProgetto(nome = …)` and its `registro` are now exposed for tests (EX-5 runs a real held transcription there).
