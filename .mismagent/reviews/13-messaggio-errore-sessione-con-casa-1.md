VERDICT: PASS
SHA: b3b128c07ad7e5d7be98002d5a49a6b7aa97f8d7
SCORES: simple=5 naming=4 modularity=4 duplication=5 concision=4 errors=5 tests=4

# Review: 13-messaggio-errore-sessione-con-casa (refactor), cycle 1

Base `de5d6574`..HEAD `b3b128c0`. `mm gate`: green (`./gradlew check`). `mm check --base de5d6574`: ok (the only
warnings are about `.mismagent/decisions/*` missing an Evidence section, which predate this slice).
The touched tests were run again with `--rerun`, so none came from the cache:
MessaggiErroreTest 18/0 fail, TestiProgettiTest 6/0, EliminazioneProgettoApertoTest 10/0, ShellPresenterTest 18/0,
ProgettiPresenterTest 19/0, ProgettiRenderCheckTest (`:ui:renderCheck`) 36/0, EX-13 ProgettoNonTrovatoTest
(`:avvio:test`) 4/0.

## Scores

| dimension | score | evidence |
|---|---|---|
| Simple design | 5 | One mapping of `ErroreSessione` (`ui/src/main/kotlin/snastro/ui/testi/MessaggiErrore.kt:40-53`). The entry point that takes the home becomes a single line (`testi/TestiProgetti.kt:71-72`). The old special case for `CartellaNonTrovata` is gone. |
| Naming | 4 | Domain words (`cartellaUtente`, `ErroreSessione`). The new test names state the behaviour (`MessaggiErroreTest` "senza la casa un ErroreSessione e' un difetto di cablaggio", `EliminazioneProgettoApertoTest:140`). |
| Modularity | 4 | Everything stays in `:ui` `testi/`. `:ui` still never reads `user.home`. |
| Duplication | 5 | Before this slice, `CartellaNonTrovata` was formatted in two places (`TestiProgetti.kt` old `:71-76` and `MessaggiErrore.kt` old `:37`). Now it is formatted once, at `MessaggiErrore.kt:43`. |
| Concision | 4 | The one-argument `messaggioPer(ErroreSessione)` overload is gone and nothing unused is left. |
| Error handling | 5 | A missing home is now a wiring defect that fails loudly (`MessaggiErrore.kt:24`), matching the unmapped branch (`:33`), as display-paths "Session errors" decides. The new test pins it. |
| Tests | 4 | `EliminazioneProgettoApertoTest:139-149` checks the user-visible text (`~/Riunione.snastro`) through the presenter seam. The test that pins the refusal gives the error the static type `ErroreDominio`, so it really exercises the dispatch. The edits to other tests only change the call form, and their assertions are equivalent. |

## Blocking

None.

## Caller check: can a production path still reach the one-argument form with an `ErroreSessione`?

No. Evidence:
- Only `SessioneProgetto.crea/apri/elimina` produce an `ErroreSessione` (`ui/.../ErroriSessione.kt`; the producers are
  in `avvio/.../progetto/SessioneProgettoImpl.kt:97-231`).
- Every consumer of those results maps with the home:
  - `ShellPresenter.kt:117`
  - `EliminazioneProgettoAperto.kt:61` (fixed here)
  - `CorsaSingola.kt:55`, which carries `ProgettiPresenter.crea/apri` (`progetti/ProgettiPresenter.kt:76,84`) and
    `FlussoEliminazione.conferma` (`progetti/FlussoEliminazione.kt:41`, passed through `.poi`).
- `Smoke.kt:84` calls `sessione.apri` but only `check`s `Esito.Ok` and never maps the error.
- The remaining one-argument callers receive errors from context use cases, never from `SessioneProgetto`. Some take
  a specific type (Modelli, Somiglianza `ErroreTrascrizione`), so overload resolution never reaches the
  `ErroreDominio` entry. Those whose static type is `ErroreDominio` are:
  - `TestiRegistrazioni.kt:154` (import)
  - `AzioniSomiglianzaProgetto.kt:236`
  - `RegistrazioniPresenter`, `StatoVoci`, `RiassuntoPresenter`, `ParlantiPresenter`, `LunghezzaRiassuntoPresenter`

  None of these can carry an `ErroreSessione` today.

## Advice (not blocking)

- The guarantee only holds at runtime, because the static type is `ErroreDominio`. The progress note records this. A
  future caller of `SessioneProgetto` that maps with the one-argument form would crash only on its failure path, so a
  test of that failure path is what would catch it.
- `messaggioPer(ErroreDominio, cartellaUtente)` lives in `TestiProgetti.kt:71` while the session mapping it delegates
  to is in `MessaggiErrore.kt:40`. Moving the two-argument entry point next to the one-argument one would put all the
  dispatch in one file.
- The "Shown by" list in display-paths could cite `ui/src/main/kotlin/snastro/ui/testi/MessaggiErrore.kt` for the
  "Session errors" paragraph.
