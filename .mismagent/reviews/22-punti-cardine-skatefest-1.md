VERDICT: PASS
SHA: 56a05b992bf3371481951be32bcc7125cc233838
SCORES: simple=4 naming=4 modularity=4 duplication=4 concision=4 errors=4 tests=4

# Review — 22-punti-cardine-skatefest (slice, diff 2a6533b6..56a05b99)

Evidence produced: `mm gate` green (`./gradlew check` + detekt), `mm check --base 2a6533b6` green (warnings are
pre-existing ADR "no Evidence" notes only). `git grep` for `RispostaV1|risposta-v1|massimoVoci|GrammaticaRisposta.per|schema v1`
in `*.kt`/`*.kts`/`*.gbnf`: no stale reference left. The production prompt is identical to spike H1's pass 1
(compared with `../snastro-h1/sintesi/adattatori/src/main/kotlin/snastro/sintesi/adattatori/ml/SpikeDuePassaggi.kt:30-45,107-114`).

| dimension | score | evidence |
|---|---|---|
| Simple design | 4 | `RispostaSommario.kt:15-24` reads one key and builds the port answer; `GrammaticaRisposta.kt:8-12` is a constant, the per-request slot is gone |
| Naming | 4 | `RispostaSommario`, `risposta-sommario.gbnf`, `GBNF`; `AmbienteProgetto.kt:113` adds `modelloLinguistico` beside the existing `val modello` (advice A2) |
| Modularity | 4 | changes stay in `:sintesi:adattatori` (prompt, grammar, reading); port, domain, UI untouched; acceptance bench drives the presenter built by `costruisciRiassuntoPresenter` (`BancoRiassunto.kt:759-762`) |
| Duplication | 4 | `SchedaRiassuntoCopiaTest.kt:965-986` extracts the scheda setup and the clipboard read now that two tests use them |
| Concision | 4 | `MisuraRisposta` drops `massimoVoci` and its three constants (`MisuraRisposta.kt:13-17`); old grammar and reader deleted, not left beside the new ones |
| Error handling | 4 | a non-matching answer stays `RispostaNonValida` (`ModelloLinguisticoLlama.kt:145-146`, tested `ModelloLinguisticoLlamaTest.kt:275-279`, `RispostaSommarioTest.kt:33-45`) |
| Tests | 4 | one behaviour each, EX markers in names (`RiassuntoSoloSommarioTest.kt:16-49`); red before by construction: the fake library answers the lists with the invented decision whenever the grammar asks for `decisioni` (`BancoRiassunto.kt:800`); model-content part proven outside the gate (A1) |

## Examples
- **EX-20** — `RiassuntoSoloSommarioTest.kt:16-21` + `SchedaRiassuntoCopiaTest.kt:949-963`: Riassumi with EX-14's
  Argomento through the real composition and real `ModelloLinguisticoLlama` gives a Sommario with four empty lists
  (so no heading, no Fonte chip); the :ui test shows no "Decisioni/Questioni aperte/Azioni/Punti chiave" text.
  Passes.
- **EX-21** — `RiassuntoSoloSommarioTest.kt:23-28`: same with no Argomento; the prompt holds none. Passes.
- **EX-26** — `RiassuntoSoloSommarioTest.kt:30-37` + `SchedaRiassuntoCopiaTest.kt:962`: Copia text equals the
  Sommario, no section titles. Passes.
- Tests of other examples: none edited (superseded EX-14..17/19 had no acceptance test; the adapter unit tests changed
  are AC-S154/S159/S180 ports, consistent with R1-8 in `.mismagent/brief.md:119-123`).

## Blocking findings
None.

## Advice (not blocking)
- **A1 (tests).** The content half of EX-20/EX-21's then (the Sommario tells D1 and Q1, never says the automatic
  movement was decided) depends on Qwen3.5 9B and cannot be gated; in the gate the assertion
  `RiassuntoSoloSommarioTest.kt:71` only checks the fake's canned text passes through, and the given is two short
  Segmenti, not skatefest, though the test names say "skatefest". The real proof is the two `benchmarkRiassunto` runs on
  main's code (`.mismagent/spikes/h1.md` § "In produzione": 63.5 s / 448 words, 58.3 s / 413 words, D1 and Q1 present,
  nothing given as decided). This route is proposed in `.mismagent/conventions-proposals.md:2` for the human to accept.
  Consider naming the gated tests on what they prove ("solo sommario, liste vuote") and leaving "skatefest" to the
  benchmark record.
- **A2 (naming).** `AmbienteProgetto` now has `val modello: ModelloLinguisticoDiProva` and `modelloLinguistico:
  ModelloLinguistico = modello` (`AmbienteProgetto.kt:113-115`): when a test passes the second one, `ambiente.modello`
  is still exposed but unused. A short KDoc note on `modello` ("ignored when `modelloLinguistico` is given") would help.
- **A3 (tests).** `RispostaSommarioTest.kt:628-686` keeps the `VociNelTesto` tests that lived in `RispostaV1Test`; they
  test `VociNelTesto`, not the answer reading, and would read better in their own test class (pre-existing placement).
- **A4.** The old reader accepted `"sommario": null`; the new one rejects it (`RispostaSommario.kt:16`). Correct, since
  the grammar cannot produce null, and the change is covered (`RispostaSommarioTest.kt:39`).

## Conventions and architecture
- First of its kind and proposed: Riassunto acceptance bench on real threads with `attendiFinche` instead of
  `StandardTestDispatcher` (`conventions-proposals.md:2`); the proposal matches `BancoRiassunto.kt:741-775`.
- ADR 0026 §5 still describes the schema-v1 list grammar; the double bound it mandates still holds (bounded grammar,
  only the string unbounded, plus `max_tokens`). The text amendment is sent to the architect in
  `design-notes.md:16`, outside the diff (ADR files untouched), on the user decision R1-8.
