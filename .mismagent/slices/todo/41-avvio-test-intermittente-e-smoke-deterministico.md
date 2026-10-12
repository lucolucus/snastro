Kind: refactor
Release: R2
Examples:

The `:avvio` suite is not deterministic, and every slice's gate runs it (release review R1-2, tests 4):
- One forced `./gradlew cleanTest check --no-build-cache --continue` failed with ":avvio:test … 418 tests completed,
  1 failed"; the other 6 `:avvio` runs were green. The failing test's name was lost (R1-2, "What I ran").
- `smoke/Smoke.kt:150-152` saves `s3-parte-2` once `voci-pannello` exists, without waiting for Voce 3's Proposta:
  slice 40 measured two different renders of the same commit (3 of 12 runs in the main checkout show the loading dot,
  tag `voce-3-proposta-caricamento`, `ui/.../registrazione/CartaVoceVista.kt:188`).
- Five `:avvio` tests build sessions or queues on an implicit `CoroutineScope(SupervisorJob())` (Dispatchers.Default)
  that the test never cancels: `progetto/SessioneProgettoImplTest.kt:70`, `progetto/SessioneProgettoImplChiudiTest.kt:67`,
  `progetto/RinominaRegistrazioneTest.kt:49`, `coda/EnumeraViaTesteTest.kt:58`,
  `coda/PosizioniNellaCodaDaCodaCondivisaTest.kt:44`. `conScopeDiProva`/`conSessioneDiProva`
  (`progetto/SessioneDiProva.kt:14-20`) already offer a scope cancelled at the end.

Assumption (not proved): the leaked scopes are a cause of the intermittent failure. Step 1 checks it.

What changes:
1. **Name the failing test first** (time-boxed to about an hour of runs). Run `./gradlew cleanTest check
   --no-build-cache --continue` repeatedly (the failure came under full `check` load), copying
   `avvio/build/test-results/test` into the scratchpad after each run, never running the smoke between a failing run
   and the copy. Record in `progress.md` how many runs, and the failing test with its message if one fails.
2. **Fix what it names**: wait on the observable condition (a state, a tag, a counted event) instead of a delay; no
   retry, no longer timeout as the fix. If nothing fails in the time box, say so in `progress.md` and do 3–4 alone.
3. **Cancel every test scope**: the five tests above use `conScopeDiProva`/`conSessioneDiProva` (or a scope they cancel
   in `finally`), so no coroutine outlives its test.
4. **Smoke**: before `salvaSchermata(outputDir, "s3-parte-2")`, also wait until no `voce-*-proposta-caricamento` tag
   exists, so the shot shows "Sembra Anna" every run.

How behaviour is kept:
- No production code changes. The suite stays green with its assertions unchanged (step 2 may change how a test waits,
  never what it asserts).
- Smoke: `s3-parte-2.png` is byte-identical across 10 runs from a `git archive` copy (it may differ from Base's render
  once, since Base could catch the loading dot); the other renders stay identical to Base.

## Decision (2026-10-12, decided: codex on delegation — the user asked to review 41–44 with codex)
Keep, with two changes: keep the one-hour limit, the kept XMLs and cancelling the scopes, but do not declare the
failure fixed if it simply does not recur. The smoke must wait explicitly for the expected Proposta ("Sembra Anna"),
not only for the absence of `proposta-caricamento` (the card also has loading, waiting and error states,
`ui/src/main/kotlin/snastro/ui/registrazione/CartaVoceVista.kt:178`); compare Base and HEAD per smoke-renders.
