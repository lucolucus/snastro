# snastro — Architecture

La mappa dell'architettura vive in `.mismagent/` e resta la fonte unica:
- [`.mismagent/architecture.md`](.mismagent/architecture.md) — stile, mappa dei moduli, dipendenze ammesse, confini, composizione, canali di enforcement;
- [`.mismagent/context-map.md`](.mismagent/context-map.md) — contesti e loro relazioni;
- [`.mismagent/code-rules.md`](.mismagent/code-rules.md) — regole del codice;
- [`.mismagent/decisions/`](.mismagent/decisions/) — ADR.

## Change log
- 2026-10-11 — conventions (decided by the user): non-fatal-faults — reload-collector rule cites refactor 36 as built.
- 2026-10-11 — conventions (decided by the user): non-fatal-faults — a reload collector guards its reads inside the
  reload function; first-load fault → error state with Riprova, later fault → shown data kept with M1(b)'s message
  (release review R1-1; refactor 36).
- 2026-10-11 — design pass R1 (release review R1-1: errors=3, simple=3).
  - Decisions brought in line with the code. [ADR 0026](.mismagent/decisions/0026-runtime-llm-jni-llama.md) §5/§6 and
    [ADR 0022](.mismagent/decisions/0022-persistenza-sintesi-6sqm.md) gain dated R1-8 notes: the grammar is the
    Sommario alone, `max_tokens` comes from the Sommario asked, and reaching it is `ripetuto`. The ADR 0041 amendment
    in `.mismagent/architecture.md` now admits a fault-specific message, as conventions non-fatal-faults has said since
    2026-10-09.
  - New [ADR 0042](.mismagent/decisions/0042-guasti-delle-fasi-segnalati.md): a transcription phase fault is reported
    through a Trascrizione port, `SegnalatoreGuasti`, not through `:supporto`'s `Segnalazione` as the review suggested.
    The reason is CR-2: `:supporto` is not reachable from `*:applicazione`.
  - Queued refactors, by risk:
    - 36: the Riassunto tab's reads and both lunghezza massima saves, under ADR 0041 §3;
    - 37: ADR 0042;
    - 38–39: Revisione with naming, then the Proposte, out of S3's `StatoVoci` (presenter-split);
    - 40: S3's two screen files split by flow (screen-split).
- 2026-10-09..11 — conventions (each decided by the user or by codex on delegation; detail in the skill's topics):
  presenter-split grows S2's holders (`FlussoComandiRiga`, `LetturaS2`), the row runner owning `io` and
  `comandoConfermato`, and one test builder per bundled presenter; non-fatal-faults: a fault-specific message needs no
  generic duplicate, plus citations of slices 31–32; acceptance-tests: S3 Riassunto tests on real threads, run time
  asserted on the fake library at the measured speed.
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
