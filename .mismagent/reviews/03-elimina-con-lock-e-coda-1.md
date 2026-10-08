VERDICT: PASS
SHA: dc82427dc466718c8b3117e8f22d757ac701fdac
SCORES: simple=4 naming=4 modularity=4 duplication=4 concision=5 errors=4 tests=4

# Review — 03-elimina-con-lock-e-coda (base 09c42577)

Evidence produced:
- `./gradlew check`: BUILD SUCCESSFUL. `mm gate`: green. `mm check --base 09c42577`: ok (only pre-existing ADR "no Evidence" warnings, outside the diff).
- `EliminaConLockECodaTest` rerun 3 times with `--rerun`: tests=2 failures=0 errors=0 each time (the proposal mentions a WAL race that failed once; not reproduced).
- No new suppression in the diff.

## Examples

| EX | test | given / when / then | verdict |
|---|---|---|---|
| EX-6 | `EliminaConLockECodaTest.kt:71` | given: "mangu 2" opened by a second real `SessioneProgettoImpl` holding the real `.lock` (`:78`); when: Elimina from the list + confirm with the name (`:81`, helper `:152-156`); then: message "Questo progetto è aperto in un'altra finestra." (`:83`), folder content unchanged (`:84`), still registered (`:85`) | proven, same strength |
| EX-11 | `EliminaConLockECodaTest.kt:94` | given: "mangu 2" closed, a summary `in_attesa` written in its real DB through the production SQL repositories, checked (`:132-145`); when: Elimina, type `mangu 2`, confirm (`:103`); then: folder gone (`:105`), entry gone from the list (`:106`) | proven, same strength |

No test of other examples edited (`MessaggiErroreTest.kt:82` only adds the new variant to the exhaustive list).

## Scores

| dimension | score | evidence |
|---|---|---|
| Simple design | 4 | `SessioneProgettoImpl.kt:202-217`: `elimina` validates and delegates; `eliminaSeNonAperto` takes the lock, does the work in `try`, releases in `finally` — one thing per function, reuses `acquisisciLock`/`rilasciaLock`/`chiudiSilenziosamente` already there. |
| Naming | 4 | `eliminaSeNonAperto`, `ProgettoApertoAltrove`, `riassuntoInAttesa`, `creaChiuso` — domain words. |
| Modularity | 4 | Error variant in `ui/.../ErroriSessione.kt:20-21` next to its siblings, message in `ui/.../testi/MessaggiErrore.kt:38`; lock handling stays in `:avvio`. No new edges. |
| Duplication | 4 | Lock helpers reused, not copied. See advice A1 on the two lock-held variants. |
| Concision | 5 | +25/-7 production lines for the behaviour; nothing unused. |
| Errors | 4 | Typed `Esito.Errore(ProgettoApertoAltrove)` (`SessioneProgettoImpl.kt:209`); release in `finally` guarded by `chiudiSilenziosamente` (`:215`); an `IOException` from `FileChannel.open` in `acquisisciLock` reaches the presenter's single boundary (`ProgettiPresenter.kt:136-146`, generic message). |
| Tests | 4 | Acceptance tests follow `references/acceptance-tests.md`: presenter over the real session, substituted boundaries in KDoc (`:46-56`), `runTest(dispatcher)` shared, scope cancelled in `@AfterEach` (`:68-69`), sessions on `conScopeDiProva`. One behaviour each, named by EX. |

Conventions proposal (`.mismagent/conventions-proposals.md:2`) matches the diff: second real session with its own `RegistroProgettiFinta` (`EliminaConLockECodaTest.kt:75`), WAL files filtered (`:159-161`).

## Blocking findings

None.

## Advice (non-blocking)

- A1 (duplication/naming): `ErroreSessione.ProgettoGiaAperto` (`ErroriSessione.kt:18`) and the new `ProgettoApertoAltrove` (`:21`) mean the same fact — the `.lock` is held by another instance — and differ only in the operation refused and its wording. Defensible (EX-6 pins a different text from `apri`'s), but if a third operation needs the check, consider one variant with the message chosen by the operation.
- A2 (tests): `contenuto` (`EliminaConLockECodaTest.kt:159`) lists only top-level names, so "niente cancellato" would not see files removed inside `audio/`/`sbobinature/`. Harmless today (the lock is checked before any deletion and the fresh project has empty subfolders), but a recursive listing would make the assertion as strong as its wording.
- A3 (errors, wording): `acquisisciLock` returns `null` also on `OverlappingFileLockException` (`SessioneProgettoImpl.kt:396`), i.e. when this same JVM holds the lock; `elimina` would then say "aperto in un'altra finestra". Not reachable from the list today (the list is shown with no project open), so just a note.
