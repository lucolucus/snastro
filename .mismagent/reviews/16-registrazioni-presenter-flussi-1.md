VERDICT: PASS
SHA: 707e47a0fec796b05db873231288e1d73a4a8f3e
SCORES: simple=4 naming=4 modularity=4 duplication=4 concision=5 errors=4 tests=4

# Review 16-registrazioni-presenter-flussi (refactor), base ef8cb066

| dimension | score | evidence |
|---|---|---|
| Simple design | 4 | `FlussoImporta.kt` / `FlussoEliminaRiga.kt` each hold one flow; `StatoS2.kt:26-47` is the single shared state helper; `azioni` binds holder functions directly (`RegistrazioniPresenter.kt:730-744`), no forwarding members. `LargeClass` gone; presenter still 784 lines and 38 functions under `TooManyFunctions` (`:98`), stated in progress.md with the next flows named, as the slice allows |
| Naming | 4 | `FlussoImporta`/`FlussoEliminaRiga` follow `FlussoEliminazione`/`FlussoTogliDallaLista`; `chiedi/annulla/conferma` read well through the holder name; `s2` (`:144`) is terse but matches the screen code used across S2 docs |
| Modularity | 4 | all `internal` in `ui/.../registrazioni`; constructor, `stato`, `azioni` preserved (presenter-split); `StatoS2` plays the role `CorsaSingola`/`StatoVoci` play elsewhere, proposed in `conventions-proposals.md` and matching the diff |
| Duplication | 4 | `aggiornaDati`/`aggiornaRiga`/`rigaLibera`/`azzeraAvviso` live once (`StatoS2.kt`); the two hand-written import guards became one `libero` (`FlussoImporta.kt:120`). Pre-existing repetition of `operazioneInCorso = false, confermaElimina = false` in `FlussoEliminaRiga.kt:80,112,125` moved as is |
| Concision | 5 | net -224 lines in the presenter for +306 in three files with long KDoc; no unused parameter (the three constructor params lost `private val`, `RegistrazioniPresenter.kt:102-117`) |
| Error handling | 4 | see "The catch-alls" below: moved verbatim, behaviour kept, CR-19b frozen counts redistributed with total unchanged (`RegoleArchitetturaliTest.kt:725-727`), rule green |
| Test quality | 4 | test edits are call-site only (`p.elimina` -> `p.azioni.elimina`, `RegistrazioniEliminaParteTest.kt:167-209`, avvio `EliminaRegistrazioneTest.kt:85-89`, `IncontroPartiTest.kt:47-111`); no assertion changed; going through `azioni` is closer to the screen seam than before |

## The catch-alls (the builder's call, judged)
Right. `catturaNonFatale` (`supporto/.../CatturaNonFatale.kt:11-19`) catches every `Throwable` except
`CancellationException` and `VirtualMachineError`, so it would swallow an `AssertionError`. The moved
blocks catch `Exception` only, and `RegistrazioniConsegnaDopoCommitTest` "L269 un Error dopo il commit si propaga..."
(lines 149-175) asserts 4 `AssertionError`s reach the scope handler, three of them through the import flow
(`FlussoImporta.invia`, `:134`). Moving `invia` onto `nonFataleRegistrato` would make that test fail; the delete
flow's catch (`FlussoEliminaRiga.kt:77`) is not covered by that test but obeys the same D-0065 policy, so treating
it the same way is consistent. A behaviour-preserving refactor cannot also change the error policy. Total of
frozen catch-alls: 8 before, 6+1+1 after. Two new file keys in the frozen list: the proposal states "a catch-all
moved into a holder moves its CR-19b frozen count with it", so the human decides on it; it is not a new occurrence.
The swallowed cause (CR-7) is pre-existing and is now in the design notes (needs an `Exception`-only logging path).

## Blocking
None.

## Advice
- A1 `FlussoEliminaRiga.kt:105-128`: the three branches of `rifiutata` could share one "close the dialog" update
  with only `erroreRiga` and the reload varying; taste, pre-existing shape.
- A2 `StatoS2.ricarica` is a `val` lambda (`StatoS2.kt:28`) while the rest are functions; fine, but a KDoc line on
  why it is injected (the presenter owns `carica`) would help the next reader.

## Evidence run
- `mm gate`: green (exit 0). `mm check --base ef8cb066`: ok.
- Forced reruns (`--rerun`, results timestamped 18:52-18:53 today): `:ui:test --tests 'snastro.ui.registrazioni.*'`
  177 tests in 19 classes, 0 failures (L269 included); `:avvio:test` `EliminaRegistrazioneTest`, `IncontroPartiTest`
  green; `:architettura-test:test` green (CR-19b counts match); `:ui:detekt --rerun` green.
- `@Suppress` lines added 3, removed 3 in the diff (moved, none new); `LargeClass` removed.
