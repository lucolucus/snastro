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

## 04-elimina-progetto-aperto — 2026-10-08 (rework 1)
- B1: the open project's delete now says on S1 what is left on disk: `ShellUiStato.SenzaProgetto.avviso` (renamed from `erroreApertura`, both states; tag `shell-avviso`), set from `avvisoEliminazione`; `ShellPresenter` takes `cartellaUtente` (composition root: `cartellaUtenteReale()`).
- B2: `chiudiSessione` returns `ArrestoProgetto`'s in-time flag; past the deadline `elimina` deletes nothing and answers `ErroreSessione.ProgettoChiusoNonEliminato`. Test: `SessioneProgettoImplEliminaApertoTest` holds `chiudiDatabase` past a 300 ms `scadenzaArresto`, then deletes from the list once released.
- Shell rule: a failure after the project was closed (`corrente == null`) goes to S1's banner, never to the gone confirmation; the `corrente` collector keeps an S1 already shown (with its banner) instead of overwriting it — without it both new UI tests fail (checked by reverting the line).
- Gate flake (environment): `ControlliAdrTest` ADR-0034/0035 scripts timed out at 60s on 3 runs while the machine was loaded (`sh adr-0035-impronte-mutate-dalla-radice.sh .` took 1:06 real; 9.2 s on the same tree minutes later); `mm gate` green on retry.

## 05-progetto-non-trovato — 2026-10-08
- New port `CartelleProgetti.esiste(percorso)` (`:progetto:applicazione`, Finta + Contratto in testFixtures, `CartelleProgettiFile` = `Files.isDirectory`); `ElencoProgetti(registro, cartelle)` sets `ProgettoVista.cartellaTrovata` at every read (EX-12 needs no cache invalidation). `:avvio` builds it once: `elencoProgetti(registro)` in `ModuloProgetto.kt` — acceptance tests use it, never `ElencoProgetti(...)` directly (ADR 0030 import rule keeps adapters out of `avvio.accettazione`).
- `SessioneProgetto.togliDallaLista(percorso)` (Unit, blocking, waited on the registry thread); `SessioneProgettoImpl.elimina` now starts with it. S1: `FlussoTogliDallaLista` on the shared `CorsaSingola`, failure in `Dati.erroreTogli`; not-found card: alert tile, "Cartella non trovata" caption, menu with only "Togli dalla lista" (tag `progetti-menu-togli-<id>`); a click still calls `apri`.
- `apri` of a missing folder → `ErroreSessione.CartellaNonTrovata(cartella)` (checked before `validaCartellaProgetto`); the old `AC-349 ... percorso inesistente ... CartellaNonValida` unit test became EX-13's, and `SessioneProgettoFinta.apri` of an unknown path answers `CartellaNonTrovata` too (contract updated). `CorsaSingola` maps errors with `messaggioPer(errore, cartellaUtente)`.
- Text inside a clickable card is merged: `onNodeWithTag` on it fails ("the unmerged tree contains '1' node"), use `onNodeWithText`. Detekt file limit: `SessioneProgettoImpl.kt` hit "12 functions, threshold 11" — lock helpers moved to `LockCartella.kt` (design note).

## 12-guasti-non-fatali-registrati — 2026-10-08
- New `internal inline fun <T> nonFataleRegistrato(operazione, blocco): T?` (`ui/src/main/kotlin/snastro/ui/GuastoNonFatale.kt`): `catturaNonFatale` + WARNING with cause on logger `snastro.ui.GuastoNonFatale`, null on failure; inline so the block may suspend. `CorsaSingola.avvia` takes a first `cosa` (operation name: crea/apri/elimina/togli) and `EliminazioneProgettoAperto.conferma` use it; CR-19b counts unchanged.
- Test spy: `registriLogUi { … }` and `LogRecord.causatoDa(guasto)` in `ui/src/test/kotlin/snastro/ui/LogUi.kt`. `thrown === guasto` fails: kotlinx.coroutines' stack-trace recovery rethrows a COPY across `withContext` (seen: record had a new `IllegalStateException: registro rotto`, identity check found none), so match the cause chain.
- Slice 14 (shell runner) and the out-of-scope degraded reads should call `nonFataleRegistrato` too; conventions `non-fatal-faults` "Shown by" now cites the helper.

## 13-messaggio-errore-sessione-con-casa — 2026-10-08
- `ErroreSessione` is mapped once, in `messaggioPer(ErroreSessione, cartellaUtente)` (`ui/.../testi/MessaggiErrore.kt`); `messaggioPer(ErroreDominio, cartellaUtente)` (`TestiProgetti.kt`) dispatches to it; the one-argument `messaggioPer(ErroreDominio)` now throws `IllegalStateException` on an `ErroreSessione` (pinned in `MessaggiErroreTest`).
- `EliminazioneProgettoAperto` passes its `cartellaUtente` (new test: a `CartellaNonTrovata` from `elimina` reads `~/Riunione.snastro`; red before the fix with `/tmp/Riunione.snastro`).
- Tests that compared against `messaggioPer(ErroreSessione.X)` now pass the home explicitly (`""` where the presenter's home is `""`): ShellPresenterTest, ProgettiPresenterTest, ProgettiRenderCheckTest, TestiProgettiTest, EliminazioneProgettoApertoTest.
- A new `:ui`/`:avvio` caller that can receive an `ErroreSessione` must hold a home; the one-arg form crashes at runtime, not at compile time (static type `ErroreDominio`). `./gradlew check` and `mm gate` green (exit 0).

## 14-shell-corsa-singola — 2026-10-08
- `ShellPresenter` apri/crea/chiudi now share one private runner `corri(cosa, operazione, applica = {})` (`T : Any`, so null from `nonFataleRegistrato` always means a fault): M3 guard via `occupata()` + `operazioneInCorso` Job, `Caricamento`, the L457a `finally` reset; no raw catch left, the stale "no logging sink" comment is gone.
- CR-19b frozen entry for `ShellPresenter.kt` removed (2 -> 0); `EliminazioneProgettoAperto` unchanged (own guard and flag).
- New tests in `ShellPresenterTest`: a thrown `apri`/`chiudi` is logged at WARNING with its cause (red before: 2 failed of 20); ShellSelettoreProgettoTest, EliminazioneProgettoApertoTest, acceptance EliminaProgettoApertoTest green unchanged.
- conventions `non-fatal-faults` "Shown by" now cites `ShellPresenter.kt` (`corri`); "Still the defect" line removed. `./gradlew check` and `mm gate` green (exit 0).
