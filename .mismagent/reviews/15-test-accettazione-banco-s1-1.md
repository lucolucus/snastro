VERDICT: PASS
SHA: ea6f5d305d5d5e53d4fdaf680e4c089ef034c69f
SCORES: simple=5 naming=4 modularity=4 duplication=5 concision=4 errors=- tests=5

Slice: 15-test-accettazione-banco-s1 (refactor, no own examples). Base be4a2b81..HEAD.

## Evidence run
- `./gradlew :avvio:test --rerun --tests 'snastro.avvio.accettazione.*'`: BUILD SUCCESSFUL; fresh XML (16:33Z), 13 testcases,
  0 failures/errors/skipped: EX-1..EX-13 (EliminaConLockECoda 2, EliminaProgettoAperto 2, EliminaProgettoDallaLista 3,
  EliminaSoloContenutiSnastro 2, ProgettoNonTrovato 4).
- EX test names: identical to base in all five files (diff of `fun \`EX-...\`` lines, base vs HEAD: no difference).
- `mm gate`: green (exit 0). `mm check --base be4a2b81`: green (only pre-existing ADR "no Evidence" warnings).
- No new `@Suppress`/`@Disabled`/baseline line in the diff.

## Scores
| dimension | score | evidence |
|---|---|---|
| Simple design | 5 | BancoS1.kt:45-99 one small base class: each helper one step (`creaChiuso`, `lista`, `shell`, two `elimina`); test bodies now read as given/when/then (EliminaProgettoDallaListaTest.kt:33-39) |
| Naming | 4 | domain words (`casa`, `documenti`, `lista`, `shell`, `creaChiuso`, `contenuto`); `BancoS1` also serves the shell tests (EliminaProgettoApertoTest) — name slightly narrower than its use |
| Modularity | 4 | harness beside ConSessione.kt as the convention says; helpers take the `snastro.ui.SessioneProgetto` interface (BancoS1.kt:58) not the internal impl; scheduling rules unchanged: presenter on `CoroutineScope(SupervisorJob() + dispatcher)` BancoS1.kt:52, cancelled `@AfterEach` BancoS1.kt:54-55, sessions via `conSessione` |
| Duplication | 5 | registry/dispatcher/scope/teardown, presenter builders, `dati`/`nomi`/`progetto`, `elimina` and `contenuto` each once (grep: no other `fun contenuto`/`FILE_DEL_WAL`/`creaChiuso` in avvio, ui, tests); ProgettoNonTrovatoTest now uses the WAL-tolerant `contenuto` (BancoS1.kt:111) |
| Concision | 4 | 308 lines removed, 183 added; nothing unused. `attendiRegistrati` (BancoS1.kt:102) moved into the shared file has one user (EliminaProgettoApertoTest) |
| Error handling | - | not touched (test-only); `contenuto` rethrows every walk failure except a vanished WAL file (BancoS1.kt:126-127) — per convention |
| Test quality | 5 | given/when/then unchanged per test (see below); tests read as the spec |

## Given/when/then check (nothing weakened)
- EX-1: inline chiediElimina/confermaElimina/advanceUntilIdle replaced by `elimina(presenter,"test")` — same three steps (BancoS1.kt:80-84); assertions unchanged.
- EX-2, EX-3: bodies unchanged; setup via `creaChiuso`+`lista` = the old loop + presenter. Only difference: `cartellaUtente` `""` -> `casa`, projects under `casa/Documents/snastro` instead of the TempDir root; no assertion reads paths for display, so nothing weaker (closer to production).
- EX-4: `nomiInLista(sessione)` (fresh presenter, assertIs Dati, names) -> `nomi(lista(sessione))` — same; message "S1 riaperta" added. Shell built with `casa` instead of `""`.
- EX-5: `AmbienteProgetto(casa, ...)` instead of a separate `@TempDir documenti` — same kind of temp root; all assertions unchanged.
- EX-6, EX-11: `creaChiuso`, `lista`, `elimina`, `contenuto` byte-for-byte the removed private ones (diff lines vs BancoS1.kt); `nomi(presenter)` = `dati(presenter).progetti.map(ProgettoVista::nome)`.
- EX-7, EX-13: `registraTest` -> `creaChiuso(sessione,"test")` (same crea/chiudi/await-registered), `apriLista` -> `lista` (same constructor args); assertions unchanged.
- EX-8: `contenuto` changed from `Set` incl. root `""` via `Files.walk` to a sorted `List` without root, skipping `progetto.db-wal`/`-shm`. Before/after comparison of the same folder: the root entry was constant, the list is not looser than the set, and the WAL exclusion is exactly the convention's "nothing deleted" rule the slice mandates. Not weakened.
- EX-9, EX-10: helper `elimina` identical; bodies unchanged.
- EX-12: local `lista` renamed `chiusa`, inline delete -> `elimina(chiusa,"test")`; same assertions.

## Conventions
- acceptance-tests "Shared harness" was already decided and in the skill (60c5531f, before base); the diff only adds the "Shown by BancoS1.kt" citation (acceptance-tests.md:39-40), which matches the code. Nothing new unproposed.

## Advice (non-blocking)
- `casa` is a public `lateinit var` (BancoS1.kt:47); `protected` would suffice for JUnit's `@TempDir` and match the other members.
- Consider a name covering both screens (e.g. `BancoProgetti`) since EliminaProgettoApertoTest uses it for the shell; taste.
- Slice text cites `./gradlew :avvio:test --rerun ...` — that is the right task (acceptance dir is an `:avvio` test source dir); confirmed it really runs the 13 tests.
