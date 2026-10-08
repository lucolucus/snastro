VERDICT: PASS
SHA: 06f7df9390ce71234bf64be151b9b80d4bb9d157
SCORES: simple=4 naming=4 modularity=5 duplication=4 concision=5 errors=4 tests=4

Diff reviewed: `git diff 4358961a..HEAD` (refactor slice, no examples). `mm gate`: check green, gate green.
`mm check --base 4358961a`: ok (only pre-existing "no Evidence" ADR warnings).

| dimension | score | evidence |
|---|---|---|
| Simple design | 4 | `ImpostazioniPresenter.kt:53`, `ModelliPresenter.kt:61`, `LunghezzaRiassuntoPresenter.kt:37`: each edge collapses to one helper call plus a null branch. `LettorePresenter.kt:85-108` nests the whole body in the lambda, with the fault branch after it. That is clear, but the function is still long. |
| Naming | 4 | Operation labels name the use case (`"salva preferenze"`, `"modifica lunghezza riassunto"`, `"riproduci $id"`). `avviata` (`LettorePresenter.kt:85`) holds `Unit?`, so it means "did not fail" more than "started". Acceptable. |
| Modularity | 5 | Only `:ui` presenters and their tests, plus the CR-19b list (`RegoleArchitetturaliTest.kt:714-717`). No new dependencies. |
| Duplication | 4 | The log line stays in `GuastoNonFatale.kt`. `nuovoIdAtteso = null; esito = Errore(...)` is still repeated in the two Lettore branches (`:102-107` and `:117-119`), as it was before the slice. The spec requires both branches. |
| Concision | 5 | Six catch-alls and four `CancellationException` imports are gone. Nothing unused is left. |
| Error handling | 4 | Matches non-fatal-faults: these are non-committing edges, so they use `nonFataleRegistrato`, log at WARNING with the cause and keep the shown message/fallback. The Lettore port-thrown `CancellationException` branch (`:110-120`) stays, as the spec asks. `catturaNonFatale` rethrows it (`CatturaEccezione.kt`), so fix-batch-12 #7 still holds, and its test is green. |
| Tests | 4 | Six new CR-7 tests, one behaviour each. Each uses `registriLogUi`/`causatoDa` on the exact thrown instance and also asserts the shown state. They would be red at base: nothing logged, so `single {}` throws. Existing test bodies are unchanged. Only the fakes gained an injectable fault (`ModelliPresenterTest.kt:26-32` default stays `IllegalStateException("errore imprevisto")`, same as the former `error(...)`). |

## Blocking
None.

## Advice (not blocking)
- `ImpostazioniPresenter.kt:63` `leggiSenzaErrori` runs outside a coroutine. A `CancellationException` thrown by the port was previously caught (it is an `Exception`) and fell back to `Preferenze()`. Now the helper rethrows it, out of the constructor. This is implausible from a file read and harmless in practice, but it is a small semantic change from the base.
- The same site still shows no generic message, which non-fatal-faults' "Message at every edge" asks for. The slice spec pinned this behaviour ("keeps ... falling back to `Preferenze()`"), and the implementer recorded it as debt in `design-notes.md`. I accept this because the slice is a behaviour-preserving refactor. A follow-up slice should close it.
- `.mismagent/conventions-proposals.md`: the "Shown by" proposal matches the diff, covering the four files and four test classes.
