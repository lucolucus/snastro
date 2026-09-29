# pre-R3-2 rework 2 (verifier FAIL at c020b93a)

## FAIL — A148 (MED) has no test that fails without its fix
The verifier put the literal 200 back in TestiRiassunto.contatoreArgomento/erroreArgomentoTroppoLungo and in both `> limiteCaratteriArgomento` comparisons in RiassuntoPresenter. RiassuntoPresenterTest and TestiRiassuntoTest stayed green, because every test injects 200.
Fix: add a presenter test built with a NON-200 limit (e.g. 10). It must assert:
- the counter reads "n/10";
- the error reads "Al massimo 10 caratteri.";
- Riassumi is blocked at 11 trimmed characters.
Prove it red on a throwaway copy with the literal 200 restored.

## Also fix: A159 ticker, a regression introduced by this change (MED, one-line guard)
At RiassuntoPresenter.kt:141-149 the tick writes `aggiornaDati { it.copy(richiesta = InCorso(trascorsoMs(istante))) }` unconditionally, using the captured `istante`. A tick that resumes just before collectLatest handles the new null can stamp InCorso onto a fresh pronto/fallito Dati. The UI then shows "Sto riassumendo" until the next reload.
Fix: guard it with `it.richiesta is RichiestaUi.InCorso && _avviatoIl.value == istante`. Add a test that reloads to richiestaAperta=null at the tick boundary.

Rules: `grep -rn PROBE` must be empty. Probes only on a throwaway copy. Do not use `git stash`. The gate is `./gradlew check --no-daemon --max-workers=2 --no-build-cache --rerun-tasks`.
