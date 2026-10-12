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

## 30-impostazioni-modelli-lettore-guasti-registrati — 2026-10-09
- `ImpostazioniPresenter` (`salva`, `leggiSenzaErrori`), `LunghezzaRiassuntoPresenter` (`carica`, `avviaModifica`: a `null` branch in the `when` beside Ok/Errore, `inCorso` reset in `finally`), `ModelliPresenter.scarica` and `LettorePresenter.avvia` on `nonFataleRegistrato`; shown messages and fallbacks unchanged. Lettore's port-thrown `CancellationException` branch (`ensureActive()`) stays, since the helper rethrows it.
- CR-19b entries for the four files removed (2, 2, 1, 1 -> 0): no `:ui` file is left in the frozen list.
- New CR-7 tests in the existing test classes (6, red before with "Collection contains no element matching the predicate"); `LunghezzaRiassuntoPresenter`'s fault paths had no test before. ui 707/707, architettura-test 459/459, `mm gate` green.
- Debt in `design-notes.md`: `leggiSenzaErrori` falls back with no message; `LettorePresenter.pausa` has no edge at all. Citation proposal in `conventions-proposals.md`.

## 22-punti-cardine-skatefest — 2026-10-10
- R1-8 built: `PromptRiassunto` is spike H1's pass 1 (only `{"sommario": "..."}`, Argomento as context: "racconta comunque tutta la riunione"), `risposta-sommario.gbnf` replaces `risposta-v1.gbnf`, `RispostaSommario` (was `RispostaV1`) reads exactly one string key and returns the port's `RispostaModello` with empty lists; `MisuraRisposta.massimoVoci` gone. Port, domain, screen and Copia unchanged: they already skip empty lists.
- First Riassunto acceptance tests (`RiassuntoSoloSommarioTest`, EX-20/21/26) on `BancoRiassunto`: the real composition (`AmbienteProgetto` gained `modelloLinguistico`) + the real `ModelloLinguisticoLlama` over a fake `:llama-jni` that answers what the grammar asks; red before with the invented decision in `decisioni`.
- Real model, main's code: `./gradlew :avvio:benchmarkRiassunto -Pcampione=<skatefest .md> [-Pargomento=…]` (new opt-in flag) — EX-20 63.5 s / 448 words, EX-21 58.3 s / 413 words, D1 and Q1 told, nothing given as decided (`.mismagent/spikes/h1.md` § "In produzione").
- Slices 24–25 (EX-23..EX-25): reuse `BancoRiassunto`; slice 24's prompt wording already landed here (pass 1 has it). ADR 0026 §5 still describes the list grammar (design note).

## 23-ciclo-fermato — 2026-10-10
- EX-18: `max_tokens` = ⌈3.5 × `MisuraRisposta.paroleSommario`⌉ + 512 (`ModelloLinguisticoLlama.maxTokens(richiesta)`, was from the cap: 35 512 at 10 000); reaching it is the new `ErroreApplicazioneSintesi.Ripetuto` → `MotivoFallimento.RIPETUTO` ("ripetuto", no SQL CHECK per ADR 0022) → "Il modello si è ripetuto: riprova." (`MESSAGGIO_MODELLO_RIPETUTO`); the tab's existing state 10 gives Riprova and keeps the previous Riassunto.
- No time budget on the `cancel` lambda: it cannot tell a loop from a long meeting. Bound derived: an hour of speech at the 10 000 cap = 6 112 tokens ≈ 407 s at 15 t/s. `benchmarkRiassunto` on skatefest after the change: 54.0 s, 581 tokens, 413 words, same Sommario as slice 22 (generation ≈ 20 t/s).
- `BancoRiassunto` gained `riassumiFinche` (wait for any tab state), `impostaLunghezzaMassima` (the tab's inline editor) and the fake's `siRipete` loop timed at `TOKEN_AL_SECONDO`.
- Open for the human (design notes): with seed 42 fixed, Riprova on the same input likely loops again (assumption); ADR 0026 §5/§6 and ADR 0022's code list need the architect's amendment.

## 24-argomento-come-contesto — 2026-10-10
- EX-23: `RiassuntoArgomentoComeContestoTest` (green at once: the gate's shape — whole meeting in the prompt with or without Argomento, "racconta comunque tutta la riunione", the model's Sommario shown whole — had landed in slice 22). `BancoRiassunto.riunioneNel(prompt)` reads a prompt's user turn; EX-20/21 test names lost "skatefest" (design note closed).
- Real model (`benchmarkRiassunto -Pargomento="Parliamo solo del sito e dell'hosting"`): slice 22's Argomento line dropped D1 (63.0 s, 447 words); the line now says "solo per capire …, non per scegliere cosa raccontare … anche le parti su altri temi, con gli accordi e i prossimi passi": EX-23 has D1, Q1 and Amplify (57.0 s), EX-20 re-measured still holds (52.9 s; D1 as "tra due e quattro settimane"). Table in `.mismagent/spikes/h1.md` § "argomento come contesto".
- A prompt change is judged by the benchmark, not the gate: one run per case at seed 42, stability across runs unmeasured.

## 25-riunione-senza-punti — 2026-10-10
- EX-24 `RiassuntoSoloChiacchiereTest`, EX-25 `RiassuntoFattoPrimaDiR1Test`: both green at once, no production change (slice 22 already gave new Riassunti the Sommario alone and kept reading stored lists and Fonti); they pin that shape.
- `BancoRiassunto` gained a recognizer seam (`RiconoscitoreCheDice(frasi)`: the Trascritto's words), `schedaRiassunto(id)`, `registraRiassuntoPronto(id, bozza)` (a pre-R1 `pronto` through the root's own `completa` against the stored Trascritto, saved in the real project file, not `superato`), `mostrato()` (wait without clicking) and `libreria.sommario`.
- Real model: new in-repo sample `.mismagent/spikes/campioni/chiacchiere-meteo-pranzo.md`; `benchmarkRiassunto` 15.1 s, 114 words, pronto, 0/0/0/0, nothing given as decided or assigned (`.mismagent/spikes/h1.md` § "riunione di sole chiacchiere"). The "Ignora chiacchiere" rule did not empty that Sommario (one run, seed 42): prompt unchanged.

## 31-messaggio-ai-bordi-riassunto-impostazioni-lettore — 2026-10-10
- `RiassuntoPresenter`: a failed download start re-reads then shows `MESSAGGIO_ERRORE_GENERICO`; a Fonte of this Parte that fails to play shows it too (another Parte: only logged, the switch follows — design note). One private `mostraErrore` now serves `riassumi`, `scaricaModello`, `apriFonte`.
- `ImpostazioniPresenter`: an unreadable preferences file still starts from `Preferenze()`, now with `errore = MESSAGGIO_ERRORE_GENERICO` (cleared by `chiudiErrore`). `LettorePresenter.pausa` runs through `nonFataleRegistrato("pausa")` on the single lane; a fault sets `Errore(generic)` only if `richiestaCorrente` is unchanged (before: escaped into `scope` unlogged).
- 5 new tests (2 in `RiassuntoPresenterGuastiTest`, 2 in `ImpostazioniPresenterTest`, 1 in `LettorePresenterTest`), red before ("expected <generic> but was <null>", "no element matching the predicate"); existing tests unchanged. ui 713/713, `./gradlew check` exit 0.
- Slice 32 (S3's silent edges) can reuse the same shape: message applied after the re-read, never before (`ricarica()` clears it). Citation update for non-fatal-faults in `conventions-proposals.md`.

## 32-messaggio-ai-bordi-s3 — 2026-10-10
- `StatoVoci.avviaTraParti`: a failed cross-Parte computation still shows no banner, and now calls `impostaErrore(MESSAGGIO_ERRORE_GENERICO)` after `pubblica()`. A superseded job never gets there, because its cancellation propagates through `nonFataleRegistrato`.
- `RegistrazionePresenter`: `sbobinaturaOSicuro` and the new `incontroOSicuro` return `Letto<T>?` (file-private; `null` = threw, `Letto(null)` = a clean empty answer). `mostraTrascritto` sets `errore = generic` on the `Dati` it publishes. `intestazioneParte` is now pure (takes the Incontro) and its fallback is unchanged.
- 3 new CR-7 tests (1 in `StatoVociGuastiTest`, 2 in `RegistrazionePresenterGuastiTest`). Before the fix they were red with "expected <generic> but was <null>" (their log assertions already passed). Existing S3 tests unchanged. ui 716/716; `mm gate` green.
- `AmbienteVoci`: the stacked KDocs (`:58-63`, `:156-157`) were each moved onto their own declaration. `traPartiRotta` now throws one `guastoTraParti` instance, so a test can match it with `causatoDa`.
- No silent S3 edge is left. Citation update for non-fatal-faults is in `conventions-proposals.md`.

## 33-s2-runner-con-io-e-conferma — 2026-10-10
- `StatoS2` receives `io`; `comandoDiRiga`'s `comando` is now a plain `() -> Esito<Unit>` port call, run as `withContext(io) { comandoConfermato(operazione, comando) }` inside `eccezioneRegistrata(operazione)`; `suEsito` stays on the presenter dispatcher. Same shape as S4's `suRiga`.
- The 7 callers (6 in `RegistrazioniPresenter`, `FlussoEliminaRiga.conferma`) pass only the port call; the operation name is written once. `FlussoEliminaRiga` still uses `io` for the player's `pausa`. `FlussoImporta`/`FlussoIncontro` unchanged.
- Behaviour kept with no test changed: ui 716/716 (`RegistrazioniConsegnaDopoCommitTest`, `RegistrazioniRinominaTest`, `RegistrazioniPresenterConcorrenzaTest` green); `mm gate` green.
- The `val comando = <property>` locals in the presenter stay: the properties share names with the public functions (`avviaElaborazione`, `ritrascrivi`, `annullaElaborazione`). Proposal `update presenter-split` (runner as built) is in `conventions-proposals.md`.

## 34-s2-comandi-di-riga-fuori-dal-presenter — 2026-10-11
- New `internal FlussoComandiRiga(s2, comandi: ComandiRigaS2)` holds S2's plain row commands and `concludiRiga` (reload through `s2.ricarica`); `ComandiRigaS2` (public data class, 6 required fields) replaces six presenter parameters (21 → 16); `azioni` binds `comandiDiRiga::…` directly. `numeroPersoneCampo`/`NUMERO_PERSONE_MIN/MAX` moved with it (shared with `FlussoIncontro`).
- With the port calls behind `comandi.`, slice 33's `val comando = <property>` locals are gone. `TooManyFunctions` stays: the presenter still has 22 functions (list read/merge, player), over detekt's default of 11.
- S2 tests build through ONE builder, `ui/src/test/kotlin/snastro/ui/registrazioni/RegistrazioniPresenterDiProva.kt` (the old flat parameters → bundles): slice 35 edits only it and `:avvio`'s `costruisciRegistrazioniPresenter`. Tests that called the removed presenter members now call `presenter.azioni.…` (4 files, no assertion changed).
- Kept: ui 716/716, avvio 416/416, `mm gate` green; smoke `s2.png` e8935777…, `s2-espansa.png` 2c2f89f2… (`shasum`), identical to the 22:21 render from slice 33's code (no `.kt` change from 4bddf4bf to Base).

## 35-s2-lettura-righe-fuori-dal-presenter — 2026-10-11
- New `internal LetturaS2(letture: LettureS2, disponibile)` builds `ListaS2` (pure read: Incontri grouping, rows, badge, queue position, elapsed time from `LettureS2.clock`); `LettureS2` is a public data class of 7 required fields. Presenter 16 → 11 parameters (`LongParameterList` kept with a reason), `private val io = io` gone, `TooManyFunctions` dropped (10 functions; detekt 1.23.8 flags at `>= 11`: it reported "with '11' functions … threshold … '11'").
- `riproduzione` from the read is only Disponibile/NonDisponibile; play/pause comes from the merge's `rifletteRiproduzione` (now file-private, `riproduzioneDi` folded in). `fondiIncontri` is file-private too, taking `incontro::espanso`. Same final state as before: the merge already re-derived every non-NonDisponibile row (L478a).
- No characterization test was needed: queue position (`RegistrazioniPresenterTest` "AC-203 IN_ATTESA…"), elapsed time ("AC-203 IN_CORSO…", 192 000 ms) and the badge (`RegistrazioniIdentificazioneTest`) are already pinned through `stato`. Only the test builder and `costruisciRegistrazioniPresenter` changed outside `:ui` main, as the convention promised.
- Kept: ui 716/716, avvio 416/416; smoke `s2.png` e8935777…, `s2-espansa.png` 2c2f89f2… (`shasum` of this slice's render), identical to slice 34's Base hashes. Architecture map unchanged (S2's read-model joins at `.mismagent/architecture.md:267` still hold).

## 36-riassunto-letture-e-lunghezza-registrate — 2026-10-11
- `RiassuntoPresenter.ricarica` now guards every read in one place (`nonFataleRegistrato` around `leggiEMostra`): a fault on the first load publishes the new `RiassuntoUiStato.Errore(MESSAGGIO_ERRORE_GENERICO)` (drawn by `SchedaRiassunto` with "Riprova", S3's shape); over shown `Dati` they stay with the generic message. Riprova = `Caricamento` + a `MutableSharedFlow` trigger merged into the single `collectLatest`.
- Both lunghezza massima edges use `eccezioneRegistrata` (S3: `null` → generic inline editor error; Impostazioni: `nonFataleRegistrato` swapped). `AmbienteRiassunto` gained `guastoVista`, `guastoLunghezza` and a `CoroutineExceptionHandler` collecting `sfuggite` (`sfuggito(guasto)`).
- 6 new tests (5 in `RiassuntoPresenterGuastiTest`, 1 D-0065 in `LunghezzaRiassuntoPresenterTest`) + 1 render-check smoke (Errore, Riprova reaches the action). Red before: "no element matching the predicate" / "Expected … Errore, actual Caricamento" / "[]"; the Riassunto D-0065 test was already green (the call was bare). ui 722/722, avvio 416/416, `./gradlew check` exit 0.
- Smoke renders identical to Base: `git archive e6a0de6d` into the scratchpad + `:avvio:test --tests …SmokeTest`, `shasum` of the 9 rendered PNGs equal (s2 e8935777…, s3-riassunto 2b1159a8…); `avvio/build/smoke/s3.png` is a stale Oct 2 file no test writes.
- Design note on `ricarica` removed; citation update for non-fatal-faults in `conventions-proposals.md`.

## 37-guasti-delle-fasi-segnalati — 2026-10-11
- New port `SegnalatoreGuasti` (Trascrizione `porte/`, `fun interface`, never throws) with `SegnalatoreGuastiContratto` + recording `SegnalatoreGuastiFinta`/`GuastoSegnalato` in testFixtures (RC-9); `PortePipeline.guasti` required. `eseguiFase` is now a file-private `PortePipeline` extension taking the id and the `motivo`: it reports, then answers `null`. `:avvio`: `segnalatoreGuastiSu(segnalazioneApp)` logs `Elaborazione di <id> fallita (<motivo>)` with the cause (WARNING).
- Slice's assumption confirmed: with the cause passed to `guasti.guasto(…)`, `./gradlew detekt` reports no `SwallowedException` at the catch (suppression dropped, only `TooGenericExceptionCaught` kept with its reason).
- Tests: the six AC-70 throw tests (4 port faults + 2 throwing `fase` signals) assert exactly one report with the same `motivo` and cause instance; the miss and INV-7 assert none, AC-69 asserts a successful run reports none. Red before: "expected: <[GuastoSegnalato(…)]> but was: <[]>". 12 other test files pass `SegnalatoreGuastiFinta()`.
- `EseguiProssimaElaborazioneServizioTest` hit detekt `LargeClass`: its helpers and constants moved to file level (no assertion changed). It is still near the threshold: a new test there belongs in a new class.
- `mm gate` green; `.mismagent/architecture.md` amendment of 2026-10-11 now says landed.

## 38-s3-revisione-fuori-da-stato-voci — 2026-10-11
- New `internal RevisioneVoci` (built inside `StatoVoci`, like `SomiglianzaVoci`) owns `selezione`, `revisioneInCorso`, the naming's pending maps, the Revisione commands, `nominaFrase`/`annullaFrase`, `riflettiFrasi` (its own `avvia`) and `barraDi`/`menuFrase`; it takes `StatoVoci`'s `lanciaComando` (one runner, passed as a lambda since the default `suGuasto` sits before the trailing `comando`), `ricaricaDopoRevisione`, `pubblica`, `impostaErrore`, `programmaSoglia` and live reads of `vista`/`dati`/`modificheBloccate`. `ricaricaDopoRevisione` sets the selection through `revisione.riallinea`.
- `azioni` binds `voci.revisione::…` and `voci.somiglianza::calcola/applica/annulla`; `StatoVoci` keeps no forwarding member. `DatiParlanti` (with `nomeDi`), `etichettaDiVoce`, `opzioniDi`, `attesaDa` are file-level in `StatoVoci.kt`, shared by both. StatoVoci 728 → 537 lines, RevisioneVoci 287.
- `TooManyFunctions` stays on both: with it removed `./gradlew :ui:detekt` reports "Class 'StatoVoci' with '27' functions" and "Class 'RevisioneVoci' with '19' functions … threshold … '11'" — the slice's own function list for the holder is over 11, so it carries a reasoned suppression (one per toolbar action), not the "no suppression" the slice hoped for.
- No characterization test needed: L274 partial re-read and the "nuova voce" stop (`StatoVociConsegnaDopoCommitTest`), AC-529's wait past the threshold (`RegistrazioneSomiglianzaTest`) are pinned through `azioni`. ui 722/722 unchanged; `mm gate` green; the 9 smoke PNGs `cmp`-identical to a `git archive 250d2505` render (s3.png is the stale Oct 2 file).
- Slice 39 (Proposte) can follow the same shape; proposal `update presenter-split` (S3 line) in `conventions-proposals.md`.

## 38-s3-revisione-fuori-da-stato-voci — 2026-10-11
- Rework 1 (review F1): naming a phrase moved out of `RevisioneVoci` into `NominaFraseVoci` (`voci.nomina`; `avvia`, `riflettiFrasi`, `nominaFrase`, `annullaFrase`, `attesaFraseDi`, `frasiPendenti`); it reads the toolbar's `MenuFrase` as `{ vista?.let(revisione::barraDi)?.frase }`, `RevisioneVoci` reads `nomina::attesaFraseDi` (so `nomina` is declared first).
- `riassegnaTutti`/`riassegnaUno`/`voceDi` (over a private `Riassegnazione` bundle, `incontroId` still read live), `menuFrase` and `voceDellaSelezione` are file-private in `RevisioneVoci.kt`. `RevisioneVoci` now has 9 functions and only the reasoned `LongParameterList`; the previous entry's "TooManyFunctions stays on both" holds now for `StatoVoci` only.
- `StatoVoci` passes the runner as one `private val lanciaComandoGenerico` (KDoc says why a reference cannot drop the defaulted `suGuasto`). Proposal `update presenter-split` rewritten to match (no `TooManyFunctions` on a holder: split by flow, file-private helpers).
- ui 722/722 with no test touched; `mm gate` green; the 10 smoke PNGs `cmp`-identical to round 1's renders (which matched Base).

## 39-s3-proposte-fuori-da-stato-voci — 2026-10-11
- New `internal ProposteVoci` owns the Proposte and the cross-Parte pair (both jobs, `runInterruptible(io)` kept): `ricalcola()` (AC-454 guard + both restarts, called by `ricaricaParlanti`), `fermaTraParti()` (L189, before the reload read) and `azzera()` (the Proposte, after `riallinea`) — two calls, not one `azzera()` as the slice said, because the Proposte were cleared only after a non-null re-read and clearing them earlier would flash 'Caricamento' during the read. `propostaDi`/`coppiaMostrata` feed the panel.
- Removing `ProposteVoci`'s 3 functions left `StatoVoci` at 24, so the card commands moved too, into `internal CarteVoci` (`voci.carte`; `invii`, `inCorsoAltrove`, `erroriCarta`, `invia` and the six card actions; it takes the runner whole by reference for its per-card `suGuasto`). `contenutoDi` is file-private; `DatiParlanti`, `voceRef`, `etichettaDiVoce`, `opzioniDi`, `attesaDa`, `trascorsiMs` moved to `DatiParlanti.kt`.
- `StatoVoci` 559 → 364 lines, 9 functions, only a reasoned `LongParameterList`: `./gradlew :ui:detekt` is clean with `TooManyFunctions` removed (CarteVoci has 10, ProposteVoci 7).
- No characterization test needed: AC-212 (`RegistrazioneIdentificazioneTest` "AC-212 … nessuna estrazione", `chiamateProposta` empty), AC-454 (`RegistrazioneVociRitrascriviTest` "AC-454 nessun lavoro di Proposta…"), L253 (`StatoVociConsegnaDopoCommitTest`) are already pinned through `stato`. ui 722/722, no test touched; `mm gate` green; the 10 smoke PNGs `cmp`-identical to Base's render (15:40, no `.kt` change from 5513e5db to Base).
- The architecture map names no S3 holder (no change); proposal `update presenter-split` (S3 line, Shown by) appended to `conventions-proposals.md`.

## 40-s3-schermate-per-flussi — 2026-10-11
- Moves only: `SchermataRegistrazione.kt` 798 → 335 lines (shell, 10 functions, no suppression) + `SegmentoItem.kt`, `IntestazioneRegistrazione.kt` (`BriciolaParte`/`SelettoreParte` `internal`, the waiting Parte uses them), `BarraSelezioneVista.kt` (`MenuRiassegna`, `BottoneDividi` extracted, `LongMethod` dropped; `AzioniFrase` lives here, not in `SegmentoItem.kt` as the slice listed, since only the toolbar calls it and so it stays `private`). `SchermataPannelloVoci.kt` 899 → 185 + `CartaVoceVista.kt`, `MenuParlanti.kt` (menu labels and `etichettaTipo` now `internal`).
- `CartaVoceVista.kt` keeps a reasoned file-level `TooManyFunctions`: without it `./gradlew :ui:detekt` reported "with '11' functions detected. Defined threshold inside files is set to '11'". Every other file is clean with no file-level suppression.
- ui 722/722, no test touched; `./gradlew check` exit 0. Smoke PNGs `cmp`-identical to Base: the `git archive HEAD` copy matched Base's 9 renders in 8 of 8 runs.
- `s3-parte-2` is racy in the main checkout: f6152cef… (Voce 3's Proposta still loading) in 3 of 12 runs there, while Base's archive gave f46b4275… 12 of 12. The cause is `Smoke.kt:151`, which does not wait for the Proposta (design note added). Compare smoke renders from a `git archive` copy.
- Proposal `update screen-split` (S3 citations) appended; design note on `MenuAltreAzioniCarta` now points at `CartaVoceVista.kt`.

## 41-avvio-test-intermittente-e-smoke-deterministico — 2026-10-12
- Step 1, no `:avvio` failure in the hour: 15 runs of `./gradlew cleanTest check --no-build-cache --continue` on a `git archive` copy of Base (08:29–09:30) plus 12 on HEAD's main checkout in parallel; every kept XML set (scratchpad `loop/runs/xml-*`, `headloop/xml-*`) is 418 tests, 0 failures. Not declared fixed (Decision): R1-2's failure stays unnamed. The archive's only steady failures are ADR-0016/0026 checks (`adr-0016-nativi-non-versionati.sh:8`: "not a git work tree"), and one run lost 12 Konsist tests in `:parlanti:applicazione` (`FileTreeWalk.kt:62`) — design note.
- Step 3: the five tests no longer leak a scope — `SessioneProgettoImplTest`/`…ChiudiTest` record each scope and cancel it in `@AfterEach` after closing the sessions; `RinominaRegistrazioneTest` closes the session and cancels its scope in `@AfterEach`; the two coda tests build the queue inside `conScopeDiProva`. No assertion changed.
- Step 4: `Smoke.kt` waits for 'Sembra' + 'Anna' under `voce-3-candidato-0` and no `voce-*-proposta-caricamento` before `s3-parte-2`. 10 renders of a HEAD `git archive` (under load) give `s3-parte-2` f46b4275… 10/10, and all 9 PNGs equal Base's archive render (`shasum`).
- `mm gate` green. Next: if the `:avvio` flake recurs, the kept-XML loop above is the way to name it.
