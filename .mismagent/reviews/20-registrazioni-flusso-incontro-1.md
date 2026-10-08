VERDICT: PASS
SHA: c5b7236463ba9c29dfafc7224ca4973ba06e9c35
SCORES: simple=4 naming=4 modularity=4 duplication=4 concision=4 errors=4 tests=4

# Review: 20-registrazioni-flusso-incontro (refactor), base f1435b8f

| dimension | score | evidence |
|---|---|---|
| Simple design | 4 | `FlussoIncontro.kt:22-90` has one reason to change (Incontro commands + chevron); `RegistrazioniPresenter.kt:143` wires it before `init`; `fondiIncontri` reads `incontro.espanso(...)` (`RegistrazioniPresenter.kt:238`) with no copy of the set |
| Naming | 4 | `FlussoIncontro`, `espanso`, `incontroLibero`, `aggiornaIncontro` follow the S2 holders' vocabulary (`FlussoImporta`, `FlussoEliminaRiga`) |
| Modularity | 4 | holder is `internal`, receives only `s2, scope, io, avviaElaborazioniDellIncontro` (`FlussoIncontro.kt:22-27`); constructor/`stato`/`azioni` unchanged (ADR 0030 §1); `azioni` binds `incontro::…` directly (`RegistrazioniPresenter.kt:676-680`), no forwarding members, per presenter-split |
| Duplication | 4 | code moved, not copied (old block removed `RegistrazioniPresenter.kt` -50 lines); `numeroPersoneCampo` widened to `internal` (`RegistrazioniPresenter.kt:685,695`) and shared rather than duplicated |
| Concision | 4 | the presenter no longer keeps `avviaElaborazioniDellIncontro` as a property (`RegistrazioniPresenter.kt:118`); unused `eccezioneRegistrata` import dropped |
| Error handling | 4 | `avviaElaborazioniIncontro` keeps `eccezioneRegistrata` + `comandoConfermato`, generic message on null, flag reset in `finally` (`FlussoIncontro.kt:59-75`); refresh now via `s2.ricarica()` which is `::carica` (`RegistrazioniPresenter.kt:140`), same behaviour |
| Tests | 4 | only call sites changed to go through `azioni` (`RegistrazioniIncontriTest.kt:233,248-255,364,372`; `IncontroPartiTest.kt:59-60`); no assertion changed |

## Evidence run
- `mm gate`: green (exit 0). `mm check --base f1435b8f`: ok (exit 0) — no CR-19b count raised.
- Forced rerun: `./gradlew :ui:test --tests 'snastro.ui.registrazioni.*' :avvio:test --tests 'snastro.avvio.progetto.IncontroPartiTest' --rerun` → ui 180/180, avvio 4/4, 0 failures.
- No new `@Suppress` in the diff. `TooManyFunctions` kept, justified in `progress.md` (32 functions vs threshold 11, was 38), as the slice allows.

## Examples
Refactor slice, no examples. Behaviour-kept clause respected: the test edits in `ui/src/test/.../registrazioni/` and avvio are call-site-only through `azioni`.

## Conventions
- presenter-split S2 rule followed (holder over `StatoS2`, before `init`, direct binding).
- Proposal in `.mismagent/conventions-proposals.md` matches the diff: adds `FlussoIncontro` to the S2 holders list and moves the non-fatal-faults "Shown by" citation for `avviaElaborazioniIncontro` (currently `non-fatal-faults.md:30` still points at `RegistrazioniPresenter.kt`, correct to update).

## Blocking
None.

## Advice (non-blocking)
- `FlussoIncontro.aggiornaIncontro` (`FlussoIncontro.kt:88-89`) is the Incontro twin of `StatoS2.aggiornaRiga`; if a second holder ever needs to touch Incontri, move it to `StatoS2` rather than copying it.
- `numeroPersoneCampo` now crosses files but still lives at the bottom of `RegistrazioniPresenter.kt`; when the row-commands flow is extracted, give it its own file.
