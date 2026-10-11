VERDICT: PASS
SHA: 0c3cfdf2c5b6e5b1be06e881cb2323a95ee0bb3a
SCORES: simple=4 naming=4 modularity=4 duplication=4 concision=5 errors=5 tests=4

# Review — 36-riassunto-letture-e-lunghezza-registrate (slice, refactor), round 1

Base `e6a0de6d`. Diff: 9 Kotlin files (+218/-14) and the `.mismagent` bookkeeping.
Evidence I produced: `mm gate` → `check: green`, `gate: green` (`./gradlew check` + detekt). `mm check --base e6a0de6d` → `check: ok`
(the only warnings are about old ADRs having no Evidence section, which is outside the diff). No new `@Suppress` (grep of
the `+` lines). The slice has no examples (`Examples:` is empty, refactor), so no EX test is in scope. No existing assertion was edited: the
two changed existing test helpers (`AmbienteRiassunto`, `verifica`'s `if`→`when`) only add fault hooks and a third branch.

| dimension | score | evidence |
|---|---|---|
| Simple design | 4 | `RiassuntoPresenter.kt:187-198`: `ricarica` = guard + `mostraLetturaFallita`, with one decision each; `leggiEMostra` is the old body unchanged. Riprova is one more source of the single `merge`/`collectLatest` (`:141`, `:148`, `:329-332`), so it stays single-flight as the slice asked. |
| Naming | 4 | Domain/glossary words: `leggiEMostra`, `mostraLetturaFallita`, `riprovaRichiesta`, `RiassuntoUiStato.Errore`; each operation is named (`"leggi il riassunto di <id>"` `:188`, `"modifica la lunghezza massima del riassunto di <id>"` `:384`). |
| Modularity | 4 | All of it is in `:ui` (presenter, UiStato, composable, AzioniRiassunto). There are no new dependencies and layering is unchanged. |
| Duplication | 4 | `SchedaRiassunto.kt:149-157` `ErroreCaricamento` is a near-twin of `SchermataRegistrazione.kt:168-177` `ErroreCaricamentoRegistrazione` (message + spacer + Riprova). The slice asks for "the same shape". Two copies are acceptable; extract them on the third (advice). |
| Concision | 5 | Nothing unused. `Letto` was correctly not introduced: the guarded block returns `Unit`, so `null` can only mean a fault, and the clean-`null` early return stays inside `leggiEMostra` (`:201`). |
| Error handling | 5 | Reads use `nonFataleRegistrato` (`:188`), and both committing lunghezza edges use `eccezioneRegistrata` (`RiassuntoPresenter.kt:385`, `LunghezzaRiassuntoPresenter.kt:70`). This matches non-fatal-faults (committing → `Exception` only, D-0065). The `null` → generic inline error is at `:404`, and the guard is reset in `finally`. Cancellation from `collectLatest` is rethrown by `catturaNonFatale` (`supporto/.../CatturaNonFatale.kt:14`), so a superseded reload never shows a false error. Every path is tested (below). |
| Test quality | 4 | `RiassuntoPresenterGuastiTest.kt:110-192` covers all five cases the slice lists: first-load fault → WARNING with cause + `Errore` + a later Cambiamento reloads (proves the collector survives); a fault over `Dati` → same `Dati` + generic message (exact `copy` equality); Riprova → `Caricamento` then `Dati`; a throwing lunghezza cmd → logged, generic editor error, Salva re-usable (`[1500, 1500]`); `Error` propagates (via `Ambiente.sfuggito`). `LunghezzaRiassuntoPresenterTest.kt:147-162` covers D-0065 on the Impostazioni edge, and the `Exception` case at `:126` is kept. Render check `SchedaRiassuntoRenderCheckTest.kt:272-286` shows that Riprova reaches the action. The first test checks two behaviours, but the slice prescribed that. |

## Blocking findings
None.

## Slice conformance
- Reads are guarded in one place inside `ricarica`, so every caller is covered (the collector, and `riassumi`/`scaricaModello`/`apriFonte` after their command).
- Fault over `Dati` → `mostraErrore(MESSAGGIO_ERRORE_GENERICO)` (`:194-196`). On first load → `Errore(MESSAGGIO_ERRORE_GENERICO)` (`:192-193`).
- Smoke screenshots: the `Dati`/`Caricamento` composables are unchanged in the diff; `Errore` is drawn only for the new state. The render-check PNG of the new state goes to `ui/build/render-check`, not `avvio/build/smoke`. The progress entry records byte-identical hashes against Base. I did not re-render, but the code-level evidence agrees.
- `conventions-proposals.md` cites the three files and two tests, and it matches the diff. The superseded design note on `ricarica` is removed.

## Advice (non-blocking)
- Duplication: when a third load-error-with-Riprova screen appears, move `ErroreCaricamento` / `ErroreCaricamentoRegistrazione` into `snastro.ui.stile` with a tag parameter.
- Unchanged behaviour (pre-existing): a clean `null` vista on first load, or after Riprova, leaves the tab on `Caricamento`. That is as at Base.
