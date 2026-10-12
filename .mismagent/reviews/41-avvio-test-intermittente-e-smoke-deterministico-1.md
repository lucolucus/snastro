VERDICT: PASS
SHA: 460f40de1f59011ba606e7c6b6bdf5217c7b6ef3
SCORES: simple=4 naming=4 modularity=- duplication=4 concision=4 errors=- tests=4

# Review: 41-avvio-test-intermittente-e-smoke-deterministico (slice, refactor)

Diff: `git diff 53a05028..460f40de`. It changes the 5 named `:avvio` tests, `avvio/.../smoke/Smoke.kt`, `progress.md`
and `design-notes.md`. No production code outside the smoke harness changed.

| dimension | score | evidence |
|---|---|---|
| Simple design | 4 | `Smoke.kt:157-161` waits on observable conditions (tags, texts) with no delay, retry or longer timeout. Each test change only bounds a scope's lifetime. |
| Naming | 4 | `propostaMostrata`, `VOCE_PROPOSTA`, `NOME_PROPOSTO`, `scopi`, `chiudiLaSessione` all use the glossary's words. |
| Modularity | - | not touched |
| Duplication | 4 | the two coda tests reuse `conScopeDiProva` (`EnumeraViaTesteTest.kt:58`, `PosizioniNellaCodaDaCodaCondivisaTest.kt:43`). `SessioneProgettoImplTest.kt:51-57,75` and `SessioneProgettoImplChiudiTest.kt:58-64,72` share the same `scopi` + `@AfterEach` pattern. That pattern mirrors their existing `sessioni` lists: 2 occurrences, below the 3+ threshold. |
| Concision | 4 | the `esisteTesto("Sembra")` check in `Smoke.kt:180` is redundant, because `voce-N-candidato-0` exists only under `StatoProposta.Pronta` with candidates, right after the "Sembra" label (`CartaVoceVista.kt:208-210,232`). It is harmless. |
| Errors | - | not touched |
| Tests | 4 | no assertion changed. In `RinominaRegistrazioneTest.kt:63-70`, `sessione.chiudi()` moved from the end of each test into `@AfterEach` with `try/finally scope.cancel()`, so it now also runs when a test fails. |

## Blocking
None.

## Checks run
- `MM gate`: green (`check: green`, `gate: green`, exit 0). `MM check --base 53a05028`: ok.
- Smoke: `SmokeTest` was rerun 4 times in the main checkout. That is the place where Base raced (3 of 12 renders were wrong, `progress.md:269`). `s3-parte-2.png` came out `f46b4275…` in 4 of 4 runs, and all 10 PNGs were identical across the runs.
  (`./gradlew :avvio:run --args=--smoke` without a fixture fails with `CartellaNonTrovata(build/smoke-fixture)`, as review 40 already noted. The PNGs come from `SmokeTest`.)
- Decision compliance:
  - The smoke waits for Voce 3's expected Proposta: "Anna" under the tag `voce-3-candidato-0`, plus "Sembra" (`Smoke.kt:157-161,177-182`). It does not rely only on the absence of `proposta-caricamento`.
  - `progress.md` says the failure did not recur in 15+12 runs, says "Not declared fixed (Decision)", and keeps the XMLs.
  - Base and HEAD renders were compared per PNG (`progress.md`, step 4).
- Scope cancellation:
  - The two coda tests: `conScopeDiProva` returns the block's value and cancels the scope (`supporto-test/.../ConScopeDiProva.kt:13`). This keeps the old "cancel right after construction" behaviour.
  - The session tests: they cancel scopes after `chiudi()` in `@AfterEach`.

## Advice (not blocking)
- In `SessioneProgettoImplTest.kt:56-57` and `SessioneProgettoImplChiudiTest.kt:63-64`, the scopes are not cancelled if a `chiudi()` throws. `RinominaRegistrazioneTest` guards against this with `try/finally`. Using the same guard in all three would match the slice's "cancel in `finally`".
- `propostaMostrata`'s KDoc says Voce N's card shows 'Sembra', but the 'Sembra' check looks across the whole screen. Dropping the check, or scoping it to the card, would make the KDoc accurate.

## Outside the diff
A line was added to `design-notes.md`: other `:avvio` tests still leak scopes (`AttesaMutexTest`, `PropostaTraPartiComposizioneTest`, `AggiornamentiVistaEventiTest`).
