VERDICT: PASS
SHA: e36d74144fea85a6ebcc491d10da1dc82dfdc8dd
SCORES: simple=4 naming=4 modularity=4 duplication=4 concision=4 errors=4 tests=4

# Review — 04-elimina-progetto-aperto (cycle 2)

Base 84519273. Rework diff reviewed: `5e77892e..e36d7414` (3be474de, e36d7414), on top of the cycle-1 review.
`mm gate` green (`check: green`, `gate: green`), `mm check --base 84519273` ok (only the old ADR "no Evidence"
warnings). The touched tests were forced to run (`--rerun`, result XML stamped 2026-10-08 15:14Z):
`SessioneProgettoImplEliminaApertoTest` 3/3, `accettazione.EliminaProgettoApertoTest` 2/2,
`EliminazioneProgettoApertoTest` 8/8, `ShellPresenterTest` 18/18, plus `ChiusuraProgettoTest`, `Navigazione*`,
`ShellRenderCheckTest`, `MessaggiErroreTest`: all green. No new suppression: `SessioneProgettoImpl.kt` has 5
`TooGenericExceptionCaught`, same as at base. The one in the diff is the old `arresta`, now written as an expression.

| dimension | score | evidence |
|---|---|---|
| Simple design | 4 | `SessioneProgettoImpl.kt:220-224`: one `if` on the in-time flag; `chiudiSessione` returns what `ArrestoProgetto.arresta` already returns (`ArrestoProgetto.kt:18`); `EliminazioneProgettoAperto.fallita` (`:70-76`) is one rule: confirmation while open, S1 banner once closed |
| Naming | 4 | `ProgettoChiusoNonEliminato` says what happened; `erroreApertura` became the neutral `avviso` (and the `shell-avviso` tag), as asked, because it now also holds the partial-delete warning |
| Modularity | 4 | the typed error lives in `ErroriSessione.kt:30`, its text in `MessaggiErrore.kt:41`; `cartellaUtente` is injected from the composition root (`Presenter.kt:35`) the same way S1 gets it (`Presenter.kt:39`) |
| Duplication | 4 | reuses `avvisoEliminazione` (`testi/TestiProgetti.kt`) and the S1 banner already there, so the list and the shell show the same text |
| Concision | 4 | the change is small and focused; the two design-note entries that hid B1/B2 are gone |
| Error handling | 4 | B1 fixed: `EliminazioneProgettoAperto.kt:56-58` puts what is left on disk on S1. B2 fixed: when the shutdown runs past the deadline, `elimina` no longer answers `ProgettoApertoAltrove` (`SessioneProgettoImpl.kt:223`), and the failure reaches S1's banner instead of `aggiorna`'s no-op (`:71-72`); the collector keeps an S1 that is already shown (`ShellPresenter.kt:50-53`), so neither order of the two writes drops the banner |
| Tests | 4 | `SessioneProgettoImplEliminaApertoTest` cycle-2 case: the real `AmbienteProgetto` with a 300 ms `scadenzaArresto` and `chiudiDatabase` held on a latch. `ArrestoProgetto` runs `poi` on the shutdown thread, so the thread outlives the join and `arresta` returns `false`. The test checks the typed error, `corrente == null`, the folder intact, the project still in the registry, then a delete from the list once the latch is released. UI: `EX-10 eliminato solo in parte lo dice sopra S1` (exact text) and `chiuso ma non eliminato lo dice sopra S1`; `MessaggiErroreTest` covers the new error |

Examples: EX-4 and EX-5 acceptance tests are unchanged apart from the new constructor argument
(`EliminaProgettoApertoTest.kt:117`); both prove the same given, when and then, and pass. Other tests were only
updated for the field rename and the constructor (`ShellPresenterTest`, `ShellRenderCheckTest`, `Navigazione*`), with
no assertion weakened.

Conventions: nothing done for the first time in the rework. The new error and the renamed banner field follow the
existing patterns; the cycle-1 proposals still match the diff.

## Blocking

None.

## Advice (not blocking)

- After `ProgettoChiusoNonEliminato`, a retry from S1 *before* the late worker ends still gets
  `ProgettoApertoAltrove` ("aperto in un'altra finestra"), which is false in that moment. The new message tells the
  user to retry "tra poco", which makes this rare. A later slice could tell this session's own deferred lock apart
  from another window's.
- `BannerAvviso` keeps the `dangerSoft` colour now that it also shows a non-error warning (partial delete). That is a
  visual choice for the human.
- The collector rule (`ShellPresenter.kt:50-53`) is only covered indirectly, by the two new UI tests (the author
  reports both fail without it; I did not repeat that mutation). A direct test named after the rule would document it.
