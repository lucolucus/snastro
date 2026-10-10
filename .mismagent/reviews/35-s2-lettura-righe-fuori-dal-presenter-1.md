VERDICT: PASS
SHA: 0a81bd55efd6c814b2ccb34fb2db948834a9ff56
SCORES: simple=4 naming=4 modularity=5 duplication=4 concision=4 errors=4 tests=4

Slice 35 (refactor, no examples). Base 93d8e8c0, reviewed `git diff 93d8e8c0..HEAD`.
`mm gate`: green (only pre-existing ADR "no Evidence" warnings). `mm check --base 93d8e8c0`: ok.

| Dimension | Score | Evidence |
|---|---|---|
| simple | 4 | `LetturaS2` is a pure builder (`LetturaS2.kt:50-97`); the presenter's `carica` calls it on `io` and keeps generations, merge and error state (`RegistrazioniPresenter.kt:105-116`). Presenter goes from 16 to 11 parameters, `TooManyFunctions` is dropped. |
| naming | 4 | `LettureS2`/`LetturaS2`/`ListaS2` follow `ComandiRigaS2`/`StatoS2`. `disponibile` says what it is. |
| modularity | 5 | The read side's 7 collaborators all go in one public bundle, built by `:avvio` (`avvio/.../Presenter.kt:78-86`). The constructor regrouping touched only the test builder and `costruisciRegistrazioniPresenter`, as the presenter-split convention says. |
| duplication | 4 | `riproduzioneDi` is folded into the single `rifletteRiproduzione` (`RegistrazioniPresenter.kt:265-273`), so no play/pause rule is left in the read. |
| concision | 4 | The redundant `private val io = io` is gone (`:93`). The moved code is verbatim. |
| errors | 4 | The non-fatal reads (`incontri`, identification, prefill) keep `nonFataleRegistrato` unchanged (`LetturaS2.kt:107,121-123,190-191`). No new catch and no new suppression: the `LongParameterList` on `costruisciRiga` was moved, it now has 6 params and detekt's default function threshold is 6, so it is still needed. The class-level suppression has a stated reason. |
| tests | 4 | No test assertion was edited, only `RegistrazioniPresenterDiProva.kt`. The suite is green (gate). Characterization tests already cover the rules through `stato`: queue position at `RegistrazioniPresenterTest.kt:385`, elapsed time 192 000 ms at `:398-418`, badge in `RegistrazioniIdentificazioneTest`, and L478a at `RegistrazioniPresenterConcorrenzaTest.kt:70`. |

## Blocking findings
None.

## Behaviour check: the row's "playing" flag
Before, `costruisciRiga` gave `NonDisponibile` when `!disponibile`, `InRiproduzione` when the snapshot `statoLettore`
matched and was playing, and `Disponibile` otherwise. Now the read gives only `NonDisponibile` or `Disponibile`
(`LetturaS2.kt:147-151`). The final state does not change, for these reasons:
- `ListaS2.righe` has one consumer, `aggiornaConNuoveRighe` (`RegistrazioniPresenter.kt:125`). At Base it already
  passed every row through `rifletteRiproduzione(fuse, lettore.stato.value)` before publishing, on every path
  including the first load (`:150`, same at Base). The merge's `nuova.copy(...)` does not read `riproduzione`.
- `rifletteRiproduzione` at Base kept `NonDisponibile` rows and otherwise called `riproduzioneDi(id, s, disponibile = true)`,
  which gives InRiproduzione or Disponibile. The new `when` (`:267-272`) is that same mapping. At Base the read's
  InRiproduzione/Disponibile distinction was therefore always overwritten. Only `NonDisponibile` mattered, and it is
  still decided by `lettore.disponibile`, called once per row per `carica` as before (AC-343).
- `rifletti` (`:187-188`) uses the same function and is unchanged.

## Advice (non-blocking)
- `LettureS2` is a `data class` of function references, so its `equals`/`copy` add nothing. This matches
  `ComandiRigaS2`, so it is kept for consistency.
- `.mismagent/conventions-proposals.md` matches the diff (file-private merge helpers, `lettore::disponibile`,
  `LetturaS2` over `LettureS2`).
