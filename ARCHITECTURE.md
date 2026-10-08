# snastro — Architecture

La mappa dell'architettura vive in `.mismagent/` e resta la fonte unica:
- [`.mismagent/architecture.md`](.mismagent/architecture.md) — stile, mappa dei moduli, dipendenze ammesse, confini, composizione, canali di enforcement;
- [`.mismagent/context-map.md`](.mismagent/context-map.md) — contesti e loro relazioni;
- [`.mismagent/code-rules.md`](.mismagent/code-rules.md) — regole del codice;
- [`.mismagent/decisions/`](.mismagent/decisions/) — ADR.

## Change log
- 2026-10-09 — conventions presenter-split (decided: codex, delegated by the user): an extracted flow's collaborators
  move into one public data class per holder, built by `:avvio`; the presenter's parameter list may change, ADR 0030 §1
  compile-time wiring kept by required non-null fields. Before, the frozen constructor meant a split could never shrink
  `RegistrazioniPresenter`'s 21 parameters (release review R0-3).
- 2026-10-08 — acceptance tests move to *tests/acceptance/src/test/kotlin*, compiled as an extra test source dir of
  `:avvio` (conventions topic acceptance-tests; decided: codex, delegated by the user): mm reads `EX-<n>` markers only
  there, and a separate Gradle module would cost settings, module-map and allowed-edge changes. Refactor slice 06.
- 2026-10-08 — design pass (R0): `.mismagent/architecture.md` (R3 registry line) now says that `:avvio`'s
  `SessioneProgetto` also deletes a project — it said open/close only, the code does more since slices 01–03
  (`SessioneProgettoImpl.elimina`). No decision changed. Queued refactors 08–11: delete flow out of
  `ProgettiPresenter` (it sits at detekt's `TooManyFunctions` limit), acceptance-test scopes and the recursive
  EX-6 check, one shared `MessaggioInlineErrore` and one "More" menu in S4.
- 2026-10-08 — design pass (R0, release review R0-1): `.mismagent/architecture.md` (R3 registry line) now names
  `togliDallaLista` and the port `CartelleProgetti` (slice 05), and states the error policy for the session port: a
  registry failure is technical, it throws and the presenter's edge logs it and shows the generic message; expected
  refusals stay `Esito`. So `togliDallaLista` returning `Unit` is not a gap (`elimina` throws on the same failure);
  the gap is the dropped cause. No decision changed. Queued refactors 12–16: log non-fatal causes in the S1 runner and
  the open-project delete, `ErroreSessione` messages only with the home, one shell runner, one acceptance harness,
  the import and row-delete flows out of `RegistrazioniPresenter`.
- 2026-10-08 — design pass (R0, release review R0-2): the faults at a presenter's edge get one rule (new
  [ADR 0041](.mismagent/decisions/0041-guasti-ai-bordi-dei-presenter.md), amending ADR 0028 §2;
  `.mismagent/architecture.md` amendment of the same date). A committing command catches `Exception` only through
  `eccezioneRegistrata` over a new `:supporto` `catturaEccezione`, so an `Error` after the commit still propagates
  (D-0065). Everything else uses `nonFataleRegistrato`. Both log.
  - Why: S2's eight raw catches drop the cause, but `nonFataleRegistrato` would swallow the `Error`s that the L269 tests
    require to propagate. The review's fix for `avviaElaborazioniIncontro` would have kept its D-0065 defect.
  - This is the target. The code moves in the queued refactors 17–21: S1's list load logged, the S2 per-row runner on
    `StatoS2`, S2's remaining edges, the Incontro flow out of `RegistrazioniPresenter`, and `SchermataRegistrazioni`
    split by flow.
- 2026-10-08 — design pass (R1, after release review R0-3): ADR 0041's amendment in `.mismagent/architecture.md` now
  says what is true. The rule holds for S1, the shell and S2. The other `:ui` presenters (S3, S4, Riassunto, settings,
  models, player) still drop causes under frozen CR-19b counts. No decision changed.
  - Queued refactors 26–30: one per edge, Riassunto first, because R1 changes that flow.
  - Not queued yet: S2's row commands out of `RegistrazioniPresenter`. Moving them cannot shrink the 21-parameter
    constructor while presenter-split says "Preserve its constructor", so that waits for the human's call on the
    proposal in `.mismagent/conventions-proposals.md`.
