VERDICT: PASS
SHA: 03d0cbf97ffd279fb0d0b72bea55098abb58a488
SCORES: simple=4 naming=4 modularity=4 duplication=5 concision=4 errors=4 tests=5

# Review — 09-test-accettazione-scope-e-contenuto (refactor, base 0ff999f2)

| dimension | score | evidence |
|---|---|---|
| Simple design | 4 | `SessioneDiProva.kt:17-26` one builder (scope, session, `chiudi` in `finally`); `ConSessione.kt:14-18` only adds `runTest(dispatcher)` on top. `EliminaConLockECodaTest.kt:147-167` `contenuto` is one visitor; a little long but at one level. |
| Naming | 4 | `conSessioneDiProva`, `conSessione`, `fileDelWal`, `FILE_DEL_WAL` (`EliminaConLockECodaTest.kt:46,169`) use the project's Italian test vocabulary. |
| Modularity | 4 | The builder sits in `:avvio` test sources (`snastro.avvio.progetto`) next to `componentiDiProva`, so both the unit test and the acceptance tests can use it (the acceptance dir compiles as `:avvio` test, acceptance-tests.md "Rule"). Recorded in `.mismagent/conventions-proposals.md:2`, and it matches the diff. |
| Duplication | 5 | The session construction written three times (`EliminaSoloContenutiSnastroTest` private `conSessione`, `EliminaConLockECodaTest.sessione`, `EliminaProgettoDallaListaTest` field, plus `SessioneProgettoImplEliminaTest.sessione`) now lives in one place. The fixed clock is defined once (`SessioneDiProva.kt:11`). |
| Concision | 4 | Leftover imports and fields are gone (`scopeSessione`, `orologio`, `GeneratoreIdFinto`, `Clock`). Advice: `OROLOGIO_DI_PROVA` (`SessioneDiProva.kt:11`) has one user, in its own file, so it could be `private`. |
| Error handling | 4 | `visitFileFailed` (`EliminaConLockECodaTest.kt:162-163`) lets through only `NoSuchFileException` on a WAL/SHM file and rethrows everything else, so no failure is swallowed. Teardown happens in `finally` (`SessioneDiProva.kt:22-25`). |
| Test quality | 5 | EX-6's "niente cancellato" is now stronger. It compares recursive relative paths and excludes only `progetto.db-wal`/`progetto.db-shm` (`EliminaConLockECodaTest.kt:143-167`). Before, the check listed top-level names and dropped every `progetto.db-*`. All sessions get cancelled scopes (`conScopeDiProva`) instead of an implicit `Dispatchers.Default`. That matches acceptance-tests.md "Wiring and scheduling" and "Another window" (the second window is a real session with its own `RegistroProgettiFinta`, closed when it ends, `EliminaConLockECodaTest.kt:79-87`). |

## Verification
- `mm gate`: green (`./gradlew check`). `mm check --base 0ff999f2`: green (only pre-existing "no Evidence" warnings on decisions).
- I ran `./gradlew :avvio:test --rerun --tests 'snastro.avvio.accettazione.*' --tests 'snastro.avvio.progetto.SessioneProgettoImplEliminaTest'` twice. Both runs exited 0, with every test passing (no failures, errors or skips): EliminaConLockECodaTest 2, EliminaProgettoDallaListaTest 3, EliminaSoloContenutiSnastroTest 2, SessioneProgettoImplEliminaTest 3. So `SessioneProgettoImpl.chiudi` returns on a `conScopeDiProva` scope, with no hang.
- Examples: this refactor slice owns none. The EX-1/2/3/6/9/10/11 tests kept their given, when and then. Only the session wiring changed, and EX-6's check got stronger. No assertion was dropped. EX-6 now registers the second window after `creaChiuso` instead of before. That does not matter: it still opens the project before the deletion attempt.
- Production code untouched: the diff covers only test sources and `.mismagent/`. No new suppressions.

## Blocking findings
None.

## Advice (not blocking)
- `SessioneDiProva.kt:11`: make `OROLOGIO_DI_PROVA` `private` (it has one user).
- File names: `ConSessione.kt` vs `SessioneDiProva.kt`. The pair reads fine, but the KDoc of `conSessione` could say why there are two layers (unit/second window vs runTest).
- Outside the diff, already in `.mismagent/design-notes.md`: `SessioneProgettoImplTest`, `SessioneProgettoImplChiudiTest` and `RinominaRegistrazioneTest` still use the implicit scope.
