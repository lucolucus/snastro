# snastro — Architecture

La mappa dell'architettura vive in `.mismagent/` e resta la fonte unica:
- [`.mismagent/architecture.md`](.mismagent/architecture.md) — stile, mappa dei moduli, dipendenze ammesse, confini, composizione, canali di enforcement;
- [`.mismagent/context-map.md`](.mismagent/context-map.md) — contesti e loro relazioni;
- [`.mismagent/code-rules.md`](.mismagent/code-rules.md) — regole del codice;
- [`.mismagent/decisions/`](.mismagent/decisions/) — ADR.

## Change log
- 2026-10-11 — design pass R2 (release review R1-2, all dimensions 4; R1 stays as tagged at b469bf03).
  - `.mismagent/architecture.md`'s ADR 0041 amendment now says refactor 36 landed and names the one cause still
    dropped, `PreferenzeAppFile.leggi`. No decision changed.
  - Queued refactors for R2, by risk:
    - 41: the intermittent `:avvio` failure named and fixed, the five never-cancelled test scopes, and `s3-parte-2`
      waiting for Voce 3's Proposta (a flaky gate costs every slice);
    - 42: one lunghezza massima rule for S3 and Impostazioni (S3 now trims, as Impostazioni does), S3's editor out of
      `RiassuntoPresenter`;
    - 43: `PreferenzeAppFile.leggi` logs its cause (CR-7);
    - 44: S3's playback lane out of `RegistrazionePresenter`, ahead of R2's jump to a point.
  - Design notes dropped: the avvio test scopes and the racy `s3-parte-2` (now 41); the CLI `--smoke` fixture (not
    worth it: the `smoke:` line runs `SmokeTest`, which builds its own fixture, and R1-2 found it reproducible).
    Rewritten: the large-presenter note keeps what 42 and 44 leave (Riassunto form, `SchedaRiassunto`, S3's read
    path); new: the preferences fallback has no user message (a contract choice).
  - Proposed: one way to compare smoke renders; a suppression's reason names its own file's cohesion.
- 2026-10-11 — conventions (decided by the user, refactors 36–40): screen-split cites S3's split screens;
  presenter-split: S3's five holders over `StatoVoci`, shared helpers in `DatiParlanti.kt`, a holder never carries
  `TooManyFunctions`; non-fatal-faults: a reload collector guards its reads (first-load fault → error with Riprova,
  later → data kept with M1(b)'s message), and application services report a mapped port fault to a port of their
  own context (ADR 0042).
- 2026-10-11 — design pass R1 (release review R1-1: errors=3, simple=3).
  - Decisions brought in line with the code. [ADR 0026](.mismagent/decisions/0026-runtime-llm-jni-llama.md) §5/§6 and
    [ADR 0022](.mismagent/decisions/0022-persistenza-sintesi-6sqm.md) gain dated R1-8 notes: the grammar is the
    Sommario alone, `max_tokens` comes from the Sommario asked, and reaching it is `ripetuto`. The ADR 0041 amendment
    in `.mismagent/architecture.md` now admits a fault-specific message, as conventions non-fatal-faults has said since
    2026-10-09.
  - New [ADR 0042](.mismagent/decisions/0042-guasti-delle-fasi-segnalati.md): a transcription phase fault is reported
    through a Trascrizione port, `SegnalatoreGuasti`, not through `:supporto`'s `Segnalazione` as the review suggested.
    The reason is CR-2: `:supporto` is not reachable from `*:applicazione`.
  - Queued refactors 36–40 (all landed): Riassunto reads and saves, ADR 0042, the S3 `StatoVoci` and screen splits.
- 2026-10-09..11 — conventions (each decided by the user or by codex on delegation; detail in the skill's topics):
  presenter-split grows S2's holders (`FlussoComandiRiga`, `LetturaS2`), the row runner owning `io` and
  `comandoConfermato`, and one test builder per bundled presenter; non-fatal-faults: a fault-specific message needs no
  generic duplicate, plus citations of slices 31–32; acceptance-tests: S3 Riassunto tests on real threads, run time
  asserted on the fake library at the measured speed.
- 2026-10-09 — design pass (R1, after refactors 26–30): ADR 0041's amendment in `.mismagent/architecture.md` now
  says it has landed in every `:ui` presenter. No `:ui` file is left in CR-19b's frozen list. What is still open is
  the user-facing half of "Message at every edge": some edges fall back silently. No decision changed.
  - Queued refactors 31–35 (all landed): the generic message at the silent edges, then S2's row runner, commands
    and row building out of `RegistrazioniPresenter`.
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
