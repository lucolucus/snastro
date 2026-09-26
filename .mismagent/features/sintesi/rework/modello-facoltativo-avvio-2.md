# Rework 2 (LAST cycle) — modello-facoltativo-avvio (head 78abdac; candidate gate RED, aborted)

## RED in the candidate (integration/sintesi @ 5009fbf + this block)
`:avvio:test` → `snastro.avvio.r2.ContenutoAppR2FooterModelloTest > AC-S163 sul grafo R2 costruito il piede mostra la riga solo durante
InDownload()` FAILED with `kotlinx.coroutines.test.UncaughtExceptionsBeforeTest: There were uncaught exceptions before the test started`
(TestScope.kt:238, runTest). Green on an immediate re-run → an exception LEAKS from an earlier test into the coroutines-test harness;
this block's new built-graph tests (ContenutoAppR1FooterModelloTest / ContenutoAppR2FooterModelloTest, AmbienteR1/AmbienteR2 real
background dispatchers, Compose test rule) are the prime suspects as source or victim.
**Fix:** find the leaking coroutine/exception (run :avvio:test repeatedly, e.g. 10×, in varied orders; check that every scope/
AmbienteR1/AmbienteR2/Compose rule opened by the new tests is closed/cancelled in `finally`/@AfterEach and that no background job throws
after the test ends); make the new tests hermetic (own scope, cancelled; no work outliving the test). Prove: 10 consecutive
`./gradlew :avvio:test --rerun-tasks` green (report the count). Do not weaken assertions. Nothing else.
This is the LAST rework cycle: re-read rework/modello-facoltativo-avvio-1.md too.
