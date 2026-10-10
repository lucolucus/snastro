# snastro — Architecture

La mappa dell'architettura vive in `.mismagent/` e resta la fonte unica:
- [`.mismagent/architecture.md`](.mismagent/architecture.md) — stile, mappa dei moduli, dipendenze ammesse, confini, composizione, canali di enforcement;
- [`.mismagent/context-map.md`](.mismagent/context-map.md) — contesti e loro relazioni;
- [`.mismagent/code-rules.md`](.mismagent/code-rules.md) — regole del codice;
- [`.mismagent/decisions/`](.mismagent/decisions/) — ADR.

## Change log
- 2026-10-10 — conventions (decided by the user): presenter-split — the S2 row runner described as built by slice 33;
  rule unchanged.
- 2026-10-10 — conventions (decided by the user): non-fatal-faults — citations only (slice 32): S3's cross-Parte
  computation and Sbobinatura/Incontro reads show the generic message; rule unchanged.
- 2026-10-10 — conventions (decided by the user): non-fatal-faults — citations only (slice 31): Lettore `pausa`, the
  generic message at Impostazioni start and Riassunto `scaricaModello`/`apriFonte`; rule unchanged.
- 2026-10-10 — conventions (decided by the user): acceptance-tests — a Riassunto run's duration is asserted on the fake
  library at the measured, conservative speed (`TOKEN_AL_SECONDO`), never with real sleeps (EX-18's 600 s).
- 2026-10-10 — conventions (decided by the user): acceptance-tests — S3 Riassunto tests extend `BancoRiassunto` on real
  threads with `attendiFinche` (the queue worker is a real thread), an explicit exception to the `StandardTestDispatcher`
  rule; the gate proves the shape, the real model's content is measured outside it (`benchmarkRiassunto`); test names
  state no data the test lacks. Topic condensed to ~300 words, rules unchanged.
- 2026-10-09 — conventions (decided: codex, delegated by the user): non-fatal-faults — an edge that already shows an
  error specific to its fault needs no extra generic message (reverses the morning's reading; avoids two messages for
  one fault); presenter-split — S2's row runner will own `withContext(io)` and `comandoConfermato` like S4's `suRiga`
  (slice 33).
- 2026-10-09 — design pass (R1, after refactors 26–30): ADR 0041's amendment in `.mismagent/architecture.md` now
  says it has landed in every `:ui` presenter. No `:ui` file is left in CR-19b's frozen list. What is still open is
  the user-facing half of "Message at every edge": some edges fall back silently. No decision changed.
  - Queued refactors 31–35:
    - 31–32: the generic message at the silent edges (Riassunto, settings, the player's `pausa`, S3);
    - 33: S2's row runner owns `io` and `comandoConfermato`, as S4's `suRiga` already does;
    - 34–35: the row commands and then the row building move out of `RegistrazioniPresenter`, each behind a public
      bundle (presenter-split 2026-10-09).
  - Proposed: an edge with a load-specific message needs no extra generic one, and one shape for the row runner.
- 2026-10-09 — conventions non-fatal-faults (codex, delegated): a non-committing edge also shows the generic
  message (review 26-1). Presenter-split (codex, delegated): an extracted flow's collaborators become one public data
  class per holder, built by `:avvio`. ADR 0030 §1 still holds through required non-null fields. Before this, the
  frozen constructor blocked shrinking `RegistrazioniPresenter` (R0-3).
- 2026-10-08 — acceptance tests moved to *tests/acceptance/src/test/kotlin*, an extra test source dir of `:avvio`
  (conventions acceptance-tests; codex, delegated). mm reads `EX-<n>` markers only there, and a Gradle module would
  have cost a module-map change. Refactor 06.
- 2026-10-08 — design passes R0 (with release reviews R0-1 and R0-2):
  - `.mismagent/architecture.md`'s R3 line now says that `SessioneProgetto` also deletes a project, and names
    `togliDallaLista`/`CartelleProgetti`.
  - Error policy of the session port: a registry failure throws, and the presenter's edge logs it and shows the
    generic message. Expected refusals stay `Esito`.
  - New [ADR 0041](.mismagent/decisions/0041-guasti-ai-bordi-dei-presenter.md), amending ADR 0028 §2: committing
    blocks use `eccezioneRegistrata` (an `Error` propagates, D-0065), every other edge uses `nonFataleRegistrato`.
    Both log.
  - Refactors 08–21 queued.
- 2026-10-08 — design pass R1 (after R0-3): ADR 0041's status was made true for S1, the shell and S2, and
  refactors 26–30 were queued.
