VERDICT: PASS
SHA: 48e4578049c58c30ba32ae00d8e80c5db70751b8
SCORES: simple=4 naming=4 modularity=4 duplication=4 concision=5 errors=5 tests=4

# Review — 28-registrazione-presenter-guasti-registrati (slice, base 118062bc)

Evidence run: `mm gate` green (`./gradlew check` and detekt included), `mm check --base 118062bc` ok. The only
warnings are about ADRs that have no Evidence: section, which this diff does not touch.
Kind: refactor, no examples. The slice's suites were not edited: the diff touches no existing test file except
`RegoleArchitetturaliTest.kt`, where the slice orders it.

| dimension | score | evidence |
|---|---|---|
| Simple design | 4 | The `carica` body moved to `mostraTrascritto` (RegistrazionePresenter.kt:155), so `carica` (:149-153) holds only the boundary and the fallback. The `avvia` wrapper is gone, and `conSbobinatura` calls the lane directly (:358-361). |
| Naming | 4 | Each caller names its operation, for example `"apri sbobinatura"` (:351), `"riproduci estratto voce ${voceId.numero}"` (:389) and `"applica somiglianza $registrazioneId"` (SomiglianzaVoci.kt:128). Renaming the lambda parameter `operazione` to `chiamata` frees `operazione` for the log label (:367, SomiglianzaVoci.kt:139). |
| Modularity | 4 | It uses only the `:ui` helpers `nonFataleRegistrato` and `eccezioneRegistrata` (GuastoNonFatale.kt:17,25), with no new dependency. |
| Duplication | 4 | Four hand-written try/catch blocks and their `@Suppress` lines are removed from the presenter, and one from SomiglianzaVoci. The test builds its presenter itself (RegistrazionePresenterGuastiTest.kt:51-72), as `RegistrazionePresenterPreRilascioTest.kt:96` already does. This repeats an existing pattern and does not block (see advice). |
| Concision | 5 | The diff removes 127 lines and adds 212, about half of them the new test. The dead `avvia` is deleted and no unused parameter is left. |
| Error handling | 5 | It follows ADR 0041 §3 exactly. The reads and the lane use `nonFataleRegistrato` (:150, :211, :213, :247, :262, :268, :369). `suIo` runs the committing `applica`, so it uses `eccezioneRegistrata` and catches `Exception` only (SomiglianzaVoci.kt:141). The previous user messages are kept: `Errore(MESSAGGIO_ERRORE_CARICAMENTO_TRASCRITTO)` (:151) and `errore = MESSAGGIO_ERRORE_GENERICO` (:370). `disponibileOSicuro` still uses null to mean the call threw, because `lettore.disponibile` returns a non-null Boolean. Cancelling the player lane (`comandoLettoreInCorso?.cancel()`) is not reported as a fault, because `catturaNonFatale` rethrows `CancellationException` (supporto/CatturaNonFatale.kt:14). CR-19b goes 4→0 and 1→0, and both entries are removed (RegoleArchitetturaliTest.kt:721). |
| Tests | 4 | Two CR-7 tests, each checking one behavior with `registriLogUi`/`causatoDa`: a thrown `trascritto` is logged at WARNING and the screen shows the load error (GuastiTest:75-88); a thrown `apriFile` is logged and the screen shows the generic error (:90-102). Before the change both edges swallowed the cause, so `single { causatoDa }` could not have passed then. That agrees with the progress note's "red before". |

## Blocking
None.

## Advice (not blocking)
- No test proves that `SomiglianzaVoci.suIo` logs a thrown `applica`, and that an `Error` from `applica` propagates
  (the split between `eccezioneRegistrata` and `nonFataleRegistrato`). The slice did not ask for such a test. A
  CR-7 test in `RegistrazioneSomiglianzaTest` would lock in the choice.
- The S3 test files each rebuild `RegistrazionePresenter` in the same way (GuastiTest:51-72, PreRilascioTest:96,
  and others). `AmbienteVoci.presenter` (AmbienteVoci.kt:314) could become the single builder, with overridable
  `trascritto` and `apriEsterno`.
- Reads now catch every non-fatal `Throwable` where they used to catch only `Exception`. This is intended (ADR 0041 §3)
  and noted in progress.md.

## Conventions
The proposal in `.mismagent/conventions-proposals.md` matches the diff: it names the same functions and the same
helper split, and cites the test file.
