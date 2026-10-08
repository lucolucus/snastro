VERDICT: PASS
SHA: 77ff2fe08fbf191672584c3ef03735843906baa9
SCORES: simple=4 naming=4 modularity=4 duplication=4 concision=4 errors=4 tests=4

# Review — 18-s2-corsa-di-riga-registrata (refactor), rework 0

Diff: `git diff d40eae96..77ff2fe0` (13 files, +234/−117).

## Evidence run
- `mm check --base d40eae96` → `check: ok`. `mm gate` (`./gradlew check`) → `gate: green`.
- Forced rerun: `./gradlew :supporto:test :ui:test :architettura-test:test --rerun-tasks` → exit 0. Fresh XML
  (17:33Z) shows supporto 23/0 failed, ui 689/0, architettura-test 459/0.
  `RegistrazioniConsegnaDopoCommitTest` ran all 3 cases green: L270 for every row command, the L269 `Error`
  propagation case (4 escaped `AssertionError`s, row not left in flight), and L270 for Trascrivi N parti.
- Test files changed: only `RegistrazioniRinominaTest.kt`, and only by adding a test. No existing test in
  `ui/src/test/.../registrazioni/` was edited.
- No new `@Suppress`. The 4 left in `RegistrazioniPresenter.kt` (:167, :410, :692, :710) existed before and match
  the frozen count of 4. Three suppressed raw catches were removed: `FlussoEliminaRiga.kt` (old :62-64) and
  `RegistrazioniPresenter.kt` (old :531-533, :497-499).

## Examples
None. This is a refactor slice. The behaviour the slice asks to keep is proven by the unchanged S2 suite plus the
new test `CR-7 un eccezione di un comando di riga e registrata a WARNING con la sua causa`
(`RegistrazioniRinominaTest.kt:179-198`). That test checks the WARNING `causatoDa(guasto)`, the generic
`erroreRiga` and the row being released.

## Scores
| dimension | score | evidence |
|---|---|---|
| Simple design | 4 | `StatoS2.comandoDiRiga` (`StatoS2.kt:50-68`) does one thing: guard, in-flight, notice, launch, logged fault, `finally`. Callers keep only their outcome (`concludiRiga`, `RegistrazioniPresenter.kt:671-677`; elimina `FlussoEliminaRiga.kt:59-68`; annulla `RegistrazioniPresenter.kt:585-591`). |
| Naming | 4 | `comandoDiRiga`, `concludiRiga`, `suEsito`, `suGuasto`, `catturaEccezione`, `eccezioneRegistrata` follow the house idiom. The `comandoConfermato` names are now per command (`"rinomina $id"` …) instead of `"comando di riga su $id"`. |
| Modularity | 4 | The catch-all stays in `:supporto` (`CatturaEccezione.kt:11-12`, built on `catturaNonFatale`, so no new catch). It is pinned in CR-18c (`RegoleArchitetturaliTest.kt:570`) and in the architecture.md `:supporto` row. The logger is now `private` (`GuastoNonFatale.kt:9`). `FlussoEliminaRiga` lost `scope` because the runner owns the launch. |
| Duplication | 4 | Three hand-written runners became one (ADR 0041 §4). One WARNING format is shared by both helpers (`GuastoNonFatale.kt:28-30`). Leftover near-duplicate: see advice 1. |
| Concision | 4 | `suRiga` and two try/catch/finally bodies are gone, along with the dead `operazioneInCorso = false` resets in `rifiutata` and in the handlers. `suGuasto` has one non-default user (elimina), as the slice asked. |
| Error handling | 4 | Every row-command `Exception` is now logged with its cause (previously `SwallowedException`). Cancellation and every `Error` propagate (`CatturaEccezione.kt:12`; tested in `CatturaEccezioneTest.kt:24-36` and by L269). The reset is in `finally` (`StatoS2.kt:66`). The CR-19b counts went down only (`RegoleArchitetturaliTest.kt:724-726`). |
| Tests | 4 | `CatturaEccezioneTest` has one behaviour per test: success, `Exception` → failure, cancellation rethrown, `Error` propagates. The new presenter test works at the `azioni` seam with the log spy. |

## Blocking findings
None.

## Advice (non-blocking)
1. **Duplication / naming.** All 7 callers write `withContext(io) { comandoConfermato("<op>") { … } }` and repeat
   the operation string twice: once as the runner's `operazione` and once inside `comandoConfermato`. Examples:
   `RegistrazioniPresenter.kt:482-487`, `:559-561`, `:610`, `FlussoEliminaRiga.kt:61-63`. The old `suRiga` applied
   `comandoConfermato` once for 5 callers. The slice requires `comandoConfermato` to stay inside the command, so this
   does not block. A local `val operazione` per call would at least stop the string from drifting. The implementer
   logged the larger alternative in design-notes (an architect's call on ADR 0041 §4).
2. **Simple design.** `conferma`, `annullaElaborazione` and `ritrascrivi` read `s2.rigaLibera(id)` themselves (to
   get the titolo, the elaborazioneId or the confirmation), and the runner reads it again. This is harmless because
   both reads are synchronous on the UI dispatcher. It is just a redundant read.
3. **Conventions proposals.** The two entries in `.mismagent/conventions-proposals.md` match the diff: the
   `comandoDiRiga` signature, the files cited, and the one `suEsito` for Ok and refusal (a deviation from the
   slice's "Ok and refusal" wording, made to stay under detekt LongParameterList).
