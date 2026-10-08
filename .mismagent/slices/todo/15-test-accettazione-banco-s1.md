Kind: refactor
Release: R0
Examples:

The acceptance-test harness is copied in each of the five test files under
`tests/acceptance/src/test/kotlin/snastro/avvio/accettazione/`: `registro`, `dispatcher`, `scopePresenter` and the
`@AfterEach` cancel (e.g. `EliminaProgettoDallaListaTest.kt:46-51`, `ProgettoNonTrovatoTest.kt:51-56`); `dati(presenter)`
4× (`EliminaProgettoDallaListaTest.kt:112`, `EliminaSoloContenutiSnastroTest.kt:122`, `EliminaConLockECodaTest.kt:171`,
`ProgettoNonTrovatoTest.kt:139`); the S1 presenter built 5× with the same arguments; two different
`contenuto(cartella)` (`EliminaConLockECodaTest.kt:147`, recursive and WAL-tolerant; `ProgettoNonTrovatoTest.kt:145`,
a plain `Files.walk`).

What changes (that directory, next to `ConSessione.kt`):
- one shared S1 harness (an abstract base class or a small fixture object — builder's call, fewest moving parts):
  the registry, the `StandardTestDispatcher`, the presenter scope cancelled after each test, a builder of
  `ProgettiPresenter`/`ShellPresenter` over a session and a home, `dati`, `elimina(presenter, nome)`;
- ONE `contenuto(cartella)`: the recursive, WAL-tolerant one (the acceptance-tests convention's rule for "nothing
  deleted"); `ProgettoNonTrovatoTest` uses it;
- the scheduling rules of the acceptance-tests convention unchanged (presenter on
  `CoroutineScope(SupervisorJob() + dispatcher)`, never `backgroundScope`; sessions through `conSessione`).

Rule served: duplication (test code). Behaviour kept: every `EX-<n>` test keeps its name and marker (mm reads them
there) and stays green — `./gradlew :avvio:test --rerun --tests 'snastro.avvio.accettazione.*'`; `./gradlew check`
green. Lands the conventions proposal "update acceptance-tests" (the harness's citation) if the human decided it.
