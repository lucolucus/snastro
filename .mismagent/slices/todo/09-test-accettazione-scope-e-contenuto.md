Kind: refactor
Release: R0
Examples:

Bring the remaining test scopes and the EX-6 "nothing deleted" check in line with the conventions topic
acceptance-tests ("Wiring and scheduling", "Another window"):
- `tests/acceptance/src/test/kotlin/snastro/avvio/accettazione/EliminaProgettoDallaListaTest.kt`: `scopeSessione =
  CoroutineScope(SupervisorJob())` runs the real session on an implicit `Dispatchers.Default`; build the session
  inside `conScopeDiProva` as `EliminaSoloContenutiSnastroTest.conSessione` already does (session closed and scope
  cancelled in `finally`), and update the KDoc's "the session keeps its own real scope" line;
- `avvio/src/test/kotlin/snastro/avvio/progetto/SessioneProgettoImplEliminaTest.kt`: same implicit scope
  (`private val scope = CoroutineScope(SupervisorJob())`) — same move;
- `EliminaConLockECodaTest.contenuto` lists only top-level names and drops every `progetto.db-*`; make it the
  recursive relative paths, excluding only `progetto.db-wal`/`progetto.db-shm`, tolerating their disappearance during
  the walk (progress.md, slice 03: a `Files.walk` hit `NoSuchFileException …/progetto.db-shm`).

If one helper ends up written twice (the `conSessione` builder, the recursive listing), put it once in the
acceptance package. Before moving a session onto a test scope, check that `SessioneProgettoImpl.chiudi` (it cancels
and waits) returns on it — a hang is a finding to report, not to work around.

Rule served: conventions topic acceptance-tests; tests as strong as their wording (review 03, A2). Behaviour kept:
production code untouched; every test listed stays green (`./gradlew check`), run three times with `--rerun` for the
two acceptance files.
