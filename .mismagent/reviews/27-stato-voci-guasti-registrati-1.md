VERDICT: PASS
SHA: 4132de2d6ea1f5011556bbb9eefbd0ed76c3e6b7
SCORES: simple=4 naming=4 modularity=4 duplication=5 concision=4 errors=4 tests=4

# Review: 27-stato-voci-guasti-registrati (slice, base fe27cddc)

## Evidence
- `mm gate`: green. `mm check --base fe27cddc`: ok. Only the usual ADR "no Evidence:" warnings, which are not part of this diff.
- Red-before check: I put the HEAD versions of `StatoVociGuastiTest.kt` and `AmbienteVoci.kt` onto the base code in a
  worktree outside the project root and ran `:ui:test --tests StatoVociGuastiTest`. Both CR-7 tests fail with
  `NoSuchElementException: Collection contains no element matching the predicate`. The L269 test passes. All three pass at HEAD.
- The diff touches only one existing test file, the fixture `AmbienteVoci.kt`. `identificazioneRotta` still throws an
  `IllegalStateException("lettura rotta")`; it is now one shared instance. `comandi` is now a delegating wrapper that
  throws `guastoComando` only when it is set. None of the existing tests is edited.
- `StatoVoci.kt` has no raw `catch`, no `catturaNonFatale` and no `getOrNull` left (grep). The diff adds no new suppression; it removes 5
  `@Suppress("TooGenericExceptionCaught","SwallowedException")`. The CR-19b entry is removed
  (`RegoleArchitetturaliTest.kt:724`).

## Scores
| dimension | score | evidence |
|---|---|---|
| simple design | 4 | `StatoVoci.kt:365-380` `lanciaComando` is one runner: publish, `eccezioneRegistrata`, `suGuasto`, then `libera` in `finally`. `invia` (`:337`), `eseguiRevisione` (`:510`) and `nominaFrase` (`:558`) each keep only their guard and their body. |
| naming | 4 | `lanciaComando`, `libera`, `suGuasto` and `operazione` read in the domain's Italian. Operation labels such as "dividi V", "unisci A in B" and "nomina la frase S" name what the user did. |
| modularity | 4 | The change stays inside `:ui` and uses `snastro.ui.eccezioneRegistrata` and `nonFataleRegistrato`. No new dependency. |
| duplication | 5 | Five hand-written try/catch/finally blocks become one runner. `val operazione` is computed once and shared between the runner log and `comandoConfermato` (`:435`, `:494`, `:579`). |
| concision | 4 | The unused `carta` binding in `invia` is gone (`:342`), and so is the `primo` parameter of `riassegnaUno` (`:478`). |
| errors | 4 | Matches ADR 0041 §3 and non-fatal-faults. Committing commands use `eccezioneRegistrata`, so an `Error` still propagates (pinned by L269). Reads `ricaricaParlanti` `:179`, `calcola` `:265`, `avviaTraParti` `:224` and `voceDi` `:487` use `nonFataleRegistrato` and keep their outcomes. Not 5: no test pins the logging for a Revisione or naming fault. |
| tests | 4 | `StatoVociGuastiTest.kt`: one behaviour per test, named after the behaviour, driven through the presenter's actions. The cause is matched by identity (`causatoDa`). The L269 test would turn red if the runner moved to `nonFataleRegistrato`. |

## Blocking findings
None.

## Checked deviations from the slice text
- The slice calls `:477` a "read" with outcome "no banner", but `:477` is `riassegnaUno`, which commits through `comandoConfermato`.
  The implementer put it on `eccezioneRegistrata`, which is what the non-fatal-faults "Committing blocks" rule requires. Before
  the change, an Exception on the first Segmento was rethrown and the outer catch turned it into the generic message
  with no reload. Now it becomes `null`, so `RisultatoRevisione(null, modificato=false)` gives the generic message with no reload: the same outcome. An
  `Error` still propagates in both cases. The L274 tests (`StatoVociConsegnaDopoCommitTest.kt:128,147`) are green and unchanged. The deviation is recorded in
  `progress.md`. Accepted.
- `ricaricaParlanti` and `calcola` used to catch `Exception` only. `nonFataleRegistrato` also catches non-VM `Error`s. The slice text and the
  non-fatal-faults rule for reads both ask for this, so it is not a finding.

## Proposals
- The `conventions-proposals.md` line (non-fatal-faults "Shown by" for S3) matches the diff: `lanciaComando`, `ricaricaParlanti`,
  `calcola`, `avviaTraParti`, `riassegnaUno`, `voceDi`, and `StatoVociGuastiTest.kt`.

## Advice (not blocking)
- One CR-7 test each for a thrown Revisione command and a thrown `nominaFrase` would cover the runner's
  default `suGuasto` path. Today only the card path (`suGuasto` overridden) is pinned.
- `assertTrue("voce" in registrato.message)` (`StatoVociGuastiTest.kt`) is a loose check of the operation label.
  Asserting the full `"comando sulla voce $V1"` prefix would pin it.

## Outside the diff
- One line added to `.mismagent/design-notes.md` about the orphaned KDoc in `AmbienteVoci.kt:156`.
