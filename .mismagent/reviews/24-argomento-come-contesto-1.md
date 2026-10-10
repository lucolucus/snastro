VERDICT: PASS
SHA: 842edf171bd10de4510f6071ac374d7ea7a7d1cb
SCORES: simple=4 naming=4 modularity=4 duplication=4 concision=4 errors=- tests=4

# Review — 24-argomento-come-contesto (slice, diff e505f44a..842edf17)

Evidence produced:
- `mm gate`: green (`./gradlew check` + detekt). `mm check --base e505f44a`: ok (warnings are the old ADR
  "no Evidence" notes only).
- `avvio/build/test-results/test/TEST-snastro.avvio.accettazione.RiassuntoArgomentoComeContestoTest.xml`: 1 test,
  0 failures. `PromptRiassuntoTest` ran with `EX-23 l'argomento e contesto per il sommario, non un filtro`.
- **Real model, run by the reviewer at HEAD:** `./gradlew :avvio:benchmarkRiassunto -Pcampione=".../2026-10-07
  SKATEFEST.md" -Pargomento="Parliamo solo del sito e dell'hosting"`. Result: PRONTO in 59.3 s, 635 tokens, 456 words,
  0/0/0/0 lists. That is the same as the spike's "slice 24" EX-23 row (`.mismagent/spikes/h1.md`, "In produzione:
  argomento come contesto"), down to the token and word counts. The Sommario has:
  - Q3: "una soluzione basata su Amazon Amplify";
  - D1: "testare le nuove meccaniche … entro due settimane o al massimo tre settimane, organizzando una sessione di
    test";
  - Q1: "caselle di colore diverso o percorsi predefiniti";
  - the automatic movement as an idea, not a decision ("Si è ipotizzata l'introduzione di un meccanismo automatico").

| dimension | score | evidence |
|---|---|---|
| Simple design | 4 | The change is one sentence of the Argomento line (`PromptRiassunto.kt:43-47`). The prompt without an Argomento does not change. |
| Naming | 4 | `riunioneNel` (`BancoRiassunto.kt:97`) and `ARGOMENTO_SUL_SITO` use the glossary's words. The EX-20/21 names drop "skatefest", which those tests do not have (rule "Test names state the example, never data the test lacks", `.claude/skills/conventions/references/acceptance-tests.md`). |
| Modularity | 4 | Production code changes only in `:sintesi:adattatori`. The acceptance test drives the presenter through `BancoRiassunto`, as the convention says. |
| Duplication | 4 | The user-turn extraction lives once, in the shared bench (`BancoRiassunto.kt:96-98`), not in the test file. |
| Concision | 4 | Nothing unused. The KDoc (`PromptRiassunto.kt:10-13`) says why the line differs from pass 1 and where that was measured. |
| Error handling | - | Not touched. |
| Tests | 4 | `RiassuntoArgomentoComeContestoTest.kt:15-28` covers one behaviour: the Argomento adds context and takes nothing away. It asserts the meeting's user turn is identical with and without the Argomento (`:21`), that the Argomento and the "tell it all" instruction reach the model (`:22-23`), and that the tab shows the model's whole Sommario (`:25-26`). `PromptRiassuntoTest` fixes the exact new wording. The content half is proven on the real model outside the gate, as the convention decides (see above). |

## Examples
- **EX-23**
  - Given: skatefest with the Argomento "Parliamo solo del sito e dell'hosting". When: Riassumi. Then: Amplify, D1 and
    Q1 are in the Sommario.
  - Gate (shape): `RiassuntoArgomentoComeContestoTest.kt:16-27` passes. It shows the Argomento does not filter the
    input and that the shown Sommario is the model's.
  - Content: the reviewer's own `benchmarkRiassunto` run above. The convention's route: the gate proves the shape,
    `benchmarkRiassunto -Pargomento` measures the content, and the spike records it.
  - The spike row with the slice-22 line (D1 lost) shows the old wording failed the example on the real model. The
    new line fixes it, so the change is needed.
- **Tests of other examples:** only renamed. The `EX-20`/`EX-21` names in `RiassuntoSoloSommarioTest.kt:17,25` lose
  the word "skatefest". Bodies and assertions are unchanged, so nothing is weakened. The rename follows the
  design-notes item it removes and the acceptance-tests convention. In `PromptRiassuntoTest.kt:65` the marker goes from
  `R1-8` to `EX-23`, and the assertion is updated to the new wording, which is this slice's behaviour.

## Blocking findings
None.

## Advice (not blocking)
- **A1 (tests).** The acceptance assertion `"racconta comunque tutta la riunione" in promptCon`
  (`RiassuntoArgomentoComeContestoTest.kt:23`) already held before this slice. Only the real-model run and the unit
  test tell the old line from the new one. Asserting the new clause instead ("non per scegliere cosa raccontare")
  would make the gated test red on the old prompt.
- **A2 (tests).** `val (promptSenza, promptCon) = libreria.prompt` (`:20`) relies on there being exactly two prompts,
  in that order, without checking it. Use `assertEquals(2, libreria.prompt.size)` or name them through `single()` after
  each run.
- **A3 (prompt, taste).** The fixed rule "Ignora chiacchiere, battute e argomenti che non c'entrano con lo scopo della
  riunione" (`PromptRiassunto.kt:38`) pulls against the new "anche le parti su altri temi" when an Argomento names a
  narrow scope. It worked on skatefest (one run, seed 42). The spike already notes that it has not been measured over
  several runs.

## Conventions and architecture
Nothing new of its kind. The bench, `attendiFinche`, and the benchmark-for-content route all follow the acceptance-tests
convention's "Riassunto" paragraph (decided 2026-10-10). Nothing to propose. Moving the design-notes item out of
`design-notes.md` is consistent: the rename it asked for is done.
