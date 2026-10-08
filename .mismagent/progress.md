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

## 15-test-accettazione-banco-s1 — 2026-10-08
- New `tests/acceptance/.../accettazione/BancoS1.kt`: abstract base `BancoS1` (`@TempDir casa`, `documenti` = `casa/Documents/snastro`, `registro`, `dispatcher`, presenter scope cancelled `@AfterEach`, `creaChiuso`, `lista`/`shell` over `casa`, `elimina` for S1 and shell, `dati`/`nomi`/`progetto`) plus top-level `contenuto` (recursive, WAL-tolerant) and `attendiRegistrati`. The five S1/shell acceptance classes extend it; 13/13 EX tests green, names unchanged.
- Helpers in a public base class can't take `internal` types (`SessioneProgettoImpl`): compile error "protected function exposes its internal parameter type" — they take the `snastro.ui.SessioneProgetto` interface.
- EX-1..EX-4 now pass the temp home instead of `""` and EX-1..4's projects live under `casa/Documents/snastro`; no assertion changed. `contenuto` no longer lists the root `""` (it compares before/after only).
- Conventions `acceptance-tests` "Shared harness" now cites `BancoS1.kt`. `./gradlew check` and `mm gate` green (exit 0).

## 16-registrazioni-presenter-flussi — 2026-10-08
- `RegistrazioniPresenter` (1008 -> 784 lines): import flow -> `FlussoImporta`, delete-a-row -> `FlussoEliminaRiga`, both over one `internal` `StatoS2` (`aggiornaDati`/`aggiornaRiga`/`rigaLibera`/`azzeraAvviso`/`ricarica` = `::carica`), also used by the presenter; `azioni` binds holder functions directly, `chiudiAvviso = s2::azzeraAvviso`. Constructor unchanged (three params now plain, not `private val`).
- `LargeClass` suppression removed (detekt green). `TooManyFunctions` still bites: `./gradlew :ui:detekt` without it reported "Class 'RegistrazioniPresenter' with '38' functions detected ... threshold ... '11'"; next flows to split: elaborazione (avvia/ritrascrivi/annulla, `suRiga`) and Incontro (`espandiIncontro`..`aggiornaIncontro`).
- Holders and `s2` are declared BEFORE `init`: its collectors may run at once on an immediate dispatcher and already call `s2`.
- CR-19b: the two catch-alls moved with their code (Error must still propagate, L269 test in `RegistrazioniConsegnaDopoCommitTest`, so `catturaNonFatale` would change behaviour); frozen counts `RegistrazioniPresenter.kt` 8 -> 6, `FlussoImporta.kt` 1, `FlussoEliminaRiga.kt` 1.
- Tests calling the moved members now go through `azioni` (`RegistrazioniEliminaParteTest`, avvio `EliminaRegistrazioneTest`, `IncontroPartiTest`); ui 687/687, avvio 409/409 forced with `--rerun`; `./gradlew check` and `mm gate` exit 0.

## 17-elenco-progetti-guasto-registrato — 2026-10-08
- `ProgettiPresenter.caricaElenco` now runs `withContext(io) { elenco.progetti() }` inside `nonFataleRegistrato("carica l'elenco dei progetti")`; null (fault) -> same `Dati(emptyList(), erroreElenco = generic)`. Raw catch and its `@Suppress` gone; CR-19b entry for `ProgettiPresenter.kt` removed (1 -> 0).
- New test in `ProgettiPresenterTest` (CR-7 ... caricamento dell elenco): the presenter is built INSIDE `registriLogUi`, since `init` launches the load (runs at `advanceUntilIdle`); red before the fix with "Collection contains no element matching the predicate".
- conventions `non-fatal-faults` "Shown by" cites `ProgettiPresenter.kt` (`caricaElenco`) as the read case. `./gradlew check` exit 0 (ui 688/688, avvio 409/409 ran), `mm gate` exit 0.

## 17-elenco-progetti-guasto-registrato — 2026-10-08 (rework 1)
- Restored `.claude/skills/conventions/references/non-fatal-faults.md` to Base 621d9586 (the human's file: `mm check --base` was red); the "Shown by" citation of `ProgettiPresenter.kt` (`caricaElenco`) is now one entry in `.mismagent/conventions-proposals.md`.
- The previous entry's last line (conventions edited) no longer holds. Never touch `.claude/skills/conventions/`: propose instead.

## 18-s2-corsa-di-riga-registrata — 2026-10-08
- `:supporto` `catturaEccezione` (built on `catturaNonFatale` + rethrow of non-`Exception`: no new catch, no suppression; CR-18c list + architecture.md row); `:ui` `eccezioneRegistrata` next to `nonFataleRegistrato`, both via one `private` `Result.registrato` (a private fun is callable from an `internal inline` one: `:ui:compileKotlin` clean), logger now `private`.
- `StatoS2(stato, scope, ricarica).comandoDiRiga(id, operazione, comando, suEsito, suGuasto = { it })`: guard, in-flight, `azzeraAvviso`, launch, logged generic `erroreRiga`, L269 `finally`. Handlers no longer reset `operazioneInCorso` (the runner does). One `suEsito` for Ok+refusal keeps it at 5 params (detekt LongParameterList). `FlussoEliminaRiga` lost its `scope`.
- CR-19b: `FlussoEliminaRiga.kt` entry removed, `RegistrazioniPresenter.kt` 6 -> 4. New test CR-7 in `RegistrazioniRinominaTest`; ui 689/689, avvio 409, supporto 23, architettura 459, all reran; `mm gate` and `mm check --base` exit 0.
- Slice 19 (S2 reads/import): `FlussoImporta` and `avviaElaborazioniIncontro` (commits through `catturaNonFatale`, D-0065 residuo) go onto `eccezioneRegistrata`; design note on the name written twice per row command.

## 19-s2-bordi-registrati — 2026-10-08
- `avviaElaborazioniIncontro` and `FlussoImporta.invia` commit through `eccezioneRegistrata` (Error propagates, Incontro released in `finally`); `carica`, `conteggiIdentificazione`, `gruppi`, `precompilato`, `riproduci`, `pausa` read through `nonFataleRegistrato`. No raw catch or `catturaNonFatale` left in S2's presenter/flows (`grep -rn "catch (\|catturaNonFatale" ui/src/main/kotlin/snastro/ui/registrazioni/` hits only `SchermataRegistrazioni.kt:1216`, `PercorsoTrascinato.kt:87`).
- CR-19b entries for `RegistrazioniPresenter.kt` (4) and `FlussoImporta.kt` (1) removed; MED residuo `pre-release.md:291` closed.
- New tests: L269 'Trascrivi N parti' in `RegistrazioniConsegnaDopoCommitTest` (red before: 0 of 1 Error reached the handler), CR-7 refresh in `RegistrazioniPresenterAggiornamentoTest` (red before: `registri: []`); ui 691/691, avvio 409, `mm gate` and `mm check --base` exit 0.
- `carica` now catches every non-fatal `Throwable` (was `Exception`), per ADR 0041 §3 for reads. Citation proposal for non-fatal-faults in `conventions-proposals.md`.

## 20-registrazioni-flusso-incontro — 2026-10-08
- `RegistrazioniPresenter` (784 -> 715 lines): `espandiIncontro`, `modificaNumeroPersoneIncontro`, `avviaElaborazioniIncontro`, `chiudiErroreIncontro`, `incontroLibero`, `aggiornaIncontro` and the `espansi` set -> `internal FlussoIncontro(s2, scope, io, avviaElaborazioniDellIncontro)`, declared before `init`; `fondiIncontri` reads `incontro.espanso(id)`; `azioni` binds `incontro::…`. After success it calls `s2.ricarica()` (= `::carica`, same behaviour).
- `NumeroPersoneCampo`/`numeroPersoneCampo` went from file-`private` to `internal` (shared by the presenter's row commands and `FlussoIncontro`).
- `TooManyFunctions` stays: `./gradlew :ui:detekt` without it reported "Class 'RegistrazioniPresenter' with '32' functions detected ... threshold ... '11'" (was 38). Next: row-commands flow and the list build (`costruisciLista` + helpers).
- Tests calling the moved members go through `azioni` (`RegistrazioniIncontriTest`, avvio `IncontroPartiTest`); ui 691/691, avvio 409/409 with `--rerun`; `mm gate` and `mm check --base` exit 0. Citation updates for presenter-split / non-fatal-faults proposed in `conventions-proposals.md`.

## 21-schermata-registrazioni-per-flussi — 2026-10-08
- `SchermataRegistrazioni.kt` 1503 -> 249 lines (shell only, no suppression); moved verbatim into `IncontroItem.kt`, `RigaRegistrazioneItem.kt` (+ `Separatore`, `ControlloRiproduzione`, shared with the Incontro row), `ColonnaElaborazione.kt`, `MenuAzioniRegistrazione.kt`, `BarraImportazione.kt`. Cross-file composables and `DIMENSIONE_INDICATORE_PICCOLO`/`SPESSORE_TRATTEGGIO_*` are `internal`. A sorted-line diff against Base shows only `private` -> `internal`, `package` lines and the suppression comment changed.
- `RigaRegistrazioneItem.kt` keeps a file-level `TooManyFunctions` with its reason: without it `./gradlew :ui:detekt` reported "'13' functions detected. Defined threshold inside files is set to '11'" (design note: the inline editor could become a file of its own).
- An import name inside a KDoc makes a text match wrongly treat the import as used; detekt `NoUnusedImports` catches it (`fillMaxSize`, `verticalScroll` in `BarraImportazione.kt`).
- ui 691/691 + renderCheck 509/509 with `--rerun`, no baseline touched; smoke `s2.png`/`s2-espansa.png` byte-identical to Base's (shasum, Base built in a scratch worktree outside the root). `mm gate` and `mm check --base` exit 0. Proposal `create screen-split` in `conventions-proposals.md`.

## 26-riassunto-guasti-registrati — 2026-10-09
- `RiassuntoPresenter`: `riassumi` commits through `eccezioneRegistrata("riassumi <id>")` (a thrown command now shows `MESSAGGIO_ERRORE_GENERICO` after `ricarica()`, like a refusal); `scaricaModello` and `apriFonte` read through `nonFataleRegistrato` (download fault -> `ricarica()`; the Parte switch still follows). No raw catch or `catturaNonFatale` left; CR-19b entry for `RiassuntoPresenter.kt` removed (2 -> 0).
- New `RiassuntoPresenterGuastiTest` (3 CR-7 tests, red before with "Collection contains no element matching the predicate"). With them inside `RiassuntoPresenterTest`, `./gradlew :ui:detekt` reported "Class RiassuntoPresenterTest is too large ... [LargeClass]": the fixture (`Ambiente`, `eseguiTest`, `dati`, `unaVista`, `ServizioModelliSpia`, `REG_1`/`REG_3`, `ISTANTE_0`, `OrologioFinto`) moved verbatim to `internal` in `ui/src/test/kotlin/snastro/ui/riassunto/AmbienteRiassunto.kt`; the 45 existing tests are unchanged.
- R1's Riassunto slices (22–25): add tests in sibling classes over `AmbienteRiassunto.kt`, not to `RiassuntoPresenterTest` (at the limit).
- ui 694/694, architettura-test 459/459; `./gradlew check`, `mm gate` exit 0. Citation proposal for non-fatal-faults in `conventions-proposals.md`.

## 27-stato-voci-guasti-registrati — 2026-10-09
- `StatoVoci`: card commands (`invia`), `eseguiRevisione` and `nominaFrase` share one private runner `lanciaComando(operazione, libera, suGuasto = generic impostaErrore, comando)` over `eccezioneRegistrata` (guard set by the caller, reset in `finally`); `ricaricaParlanti`, `calcola`, `avviaTraParti`, `voceDi` read through `nonFataleRegistrato`. No raw catch or `catturaNonFatale` left in the file; CR-19b entry removed (5 -> 0).
- `riassegnaUno` (slice text called it a read at `:477`) is a committing step: it went on `eccezioneRegistrata`, not `nonFataleRegistrato`, so an Error still propagates (D-0065); its `primo` rethrow is gone, since a first-Segmento fault as `null` gives the same outcome (no reload, generic message; L274 tests green unchanged).
- New `StatoVociGuastiTest` (2 CR-7 red before with NoSuchElementException, 1 L269 green before/after). `AmbienteVoci` gained `guastoComando` (the port's `esegui` throws it: `ComandiVoceFinta` turns a body's Exception into `Errore`) and one `guastoLettura` instance.
- ui 697/697, architettura-test 459/459. Citation proposal in `conventions-proposals.md`.

## 28-registrazione-presenter-guasti-registrati — 2026-10-09
- `RegistrazionePresenter`: `carica` (body extracted to `mostraTrascritto`, fault -> `Errore(MESSAGGIO_ERRORE_CARICAMENTO_TRASCRITTO)`), `sbobinaturaOSicuro`, `disponibileOSicuro` (null still = threw), `statoSenzaTrascritto`, `intestazioneParte` and the lane `eseguiSuLane(dispatcher, operazione, chiamata)` on `nonFataleRegistrato`; `avvia` is gone (`conSbobinatura` calls the lane), every lane caller names its operation. Reads now catch every non-fatal `Throwable` (was `Exception`), as in slice 19.
- `SomiglianzaVoci.suIo(operazione, chiamata)` on `eccezioneRegistrata` (runs the committing `applica`; `Exception` only, as before).
- CR-19b entries for both files removed (4 -> 0, 1 -> 0). New `RegistrazionePresenterGuastiTest` (2 CR-7 tests, red before with "Collection contains no element matching the predicate"); it reuses the package's `internal val REG` from `AmbienteVoci.kt` (a file-private `REG` there does not compile: "Conflicting declarations").
- ui 699/699, architettura-test 459/459, `:ui:detekt` clean; `mm gate` green. Citation proposal in `conventions-proposals.md`.

## 29-parlanti-guasti-registrati — 2026-10-09
- `ParlantiPresenter`: `suRiga(id, operazione, comando)` runs `withContext(io) { comandoConfermato(operazione, comando) }` inside `eccezioneRegistrata(operazione)` (the name is written once per command, not twice as in S2); Ok/refusal in `concludi`; L269 reset in `finally`. `carica` and `riproduci` on `nonFataleRegistrato` (now every non-fatal `Throwable`, was `Exception`); M1 generation check and M5 `segnalaErroreDiCaricamento` kept.
- No raw catch left in the file; CR-19b entry for `ParlantiPresenter.kt` removed (3 -> 0).
- New `ParlantiPresenterGuastiTest` (2 CR-7 tests, red before with "Collection contains no element matching the predicate"); `ParlantiPresenterTest` unchanged. ui 701/701, `./gradlew check` exit 0.
- Remaining frozen `:ui` sites: Impostazioni (2), LunghezzaRiassunto (2), Lettore (1), Modelli (1). Citation proposal in `conventions-proposals.md`.
