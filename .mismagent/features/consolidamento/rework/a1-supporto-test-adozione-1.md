# Rework a1-supporto-test-adozione — cycle 1 (verifier FAIL on d6a1cf2)

## HIGH 1 — AC-C42: the import swap silently halved ~100 waits in :avvio
The removed `snastro.avvio.r1.attendiFinche` (AmbienteR1.kt:127 at 32e7fbe) defaulted to **10 s**; the shared
`snastro.supporto.test.attendiFinche` defaults to **5 s**. Every migrated call in :avvio (ComposizioneR1/R2/R3Test,
AttesaMutexR2Test, EliminaRegistrazioneR2Test, SomiglianzaR2Test, RitrascriviR2Test, AmbienteR2/R3,
ContenutoAppR*FooterModelloTest, …) that passed no explicit timeout lost half its budget.
Reproduced: `./gradlew :avvio:test --tests '*RitrascriviR2Test' --rerun-tasks --no-daemon --max-workers=2` → 2/10 red on
`AC-479 annullare una Ritrascrizione in coda…` at RitrascriviR2Test.kt:171, "(non vero entro 5s)".
Fix: audit every migrated call site in :avvio without an explicit timeout; restore the old budget where it was
reduced (e.g. a test-local `private val ATTESA = 10.seconds` passed explicitly, or an explicit `timeout = 10.seconds`).
Do NOT change the pinned :supporto-test default (5 s). A wait must never assert less than before.

## HIGH 2 — AC-C43: "20 consecutive green" not demonstrated for RegistrazioneSomiglianzaTest AC-536
Re-running `./gradlew :ui:test --tests '*RegistrazioneAttesaTest' --tests '*RegistrazioneSomiglianzaTest' --rerun-tasks
-Dkotlinx.coroutines.debug=on` 5× → 1 red: `AC-536 calcola, applica e nominaFrase non girano mai sul thread UI` —
"comando nominaFrase eseguito (non vero entro 10s)". Budget unchanged (10 s), so the non-determinism is the test's
real-thread design, not a budget cut. Fix the ROOT cause so the test is deterministic (e.g. inject a controllable
dispatcher/executor or a latch the fake signals when the command ran, instead of polling a real background thread
under load) — keeping what the AC asserts (the three commands never run on the UI thread).

## Evidence required on return
Run each named class ≥ 20× with `--rerun-tasks` on this worktree, report the exact command and the count, and say
whether another gate was running concurrently (the machine is shared; the tests must survive load).
