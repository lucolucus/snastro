VERDICT: PASS
SHA: 2a823f18a4c66977791e5cd0e46c2e4478b8f99c
SCORES: simple=4 naming=4 modularity=4 duplication=4 concision=4 errors=- tests=4

# Review — 25-riunione-senza-punti (diff 9a875def..2a823f18)

Evidence produced: `mm gate` green (check: green, gate: green, exit 0); `mm check --base 9a875def` ok (only the
pre-existing ADR "no Evidence" warnings). Test reports: `RiassuntoSoloChiacchiereTest` 1/1 and
`RiassuntoFattoPrimaDiR1Test` 1/1, 0 failures. The diff touches no production code: only the acceptance bench, two new
acceptance tests, a benchmark sample, and the notes in `h1.md` and `progress.md`. No new suppression.

## Scores

| dimension | score | evidence |
|---|---|---|
| Simple design | 4 | `BancoRiassunto.kt:72-100`: each helper does one thing (`registrazioneTrascritta`, `schedaRiassunto(id)`, `registraRiassuntoPronto`, `mostrato`). The pre-R1 Riassunto is built through the root's own `conAvvio().conCompletamento(...)` and saved in the real project file, with no back door (`BancoRiassunto.kt:84-94`). |
| Naming | 4 | Glossary words throughout (`Riassunto`, `Sommario`, `Fonte`, `Segmento`, `Incontro`). Test names state only what the test has, as the 2026-10-10 convention asks (`RiassuntoSoloChiacchiereTest.kt:17`, `RiassuntoFattoPrimaDiR1Test.kt:19`). |
| Modularity | 4 | Test-only. It uses the published testFixtures (`unRiassunto`/`conAvvio`/`conCompletamento`) and the `AmbienteProgetto` seams, and it does not reach into internals. |
| Duplication | 4 | `CON_LISTE`/`SOLO_SOMMARIO` became one `soloSommario()` over a settable `sommario` (`BancoRiassunto.kt:155-157,182`). Advice A1 covers a small repeat. |
| Concision | 4 | Everything added has a user. The default `riconoscitore` keeps the existing subclasses unchanged (`BancoRiassunto.kt:54`). |
| Errors | - | Not touched. |
| Tests | 4 | Both tests run at the composition seam on the real project file, in about 2 s each. They follow the acceptance-tests convention: real threads plus `attendiFinche`, and the gate proves the shape while `benchmarkRiassunto` measures the model's content (`h1.md` § "riunione di sole chiacchiere", sample in the repo). |

## Examples

- **EX-24**: `RiassuntoSoloChiacchiereTest.kt:17`. The given is a Trascritto made of chit-chat (`RiconoscitoreCheDice(CHIACCHIERE)`), and the test asserts that this chit-chat reaches the model (`:23-24`). The when is Riassumi. The then is checked as follows:
  - `pronto`: contenuto is non-null only from a `pronto` Riassunto (`RiassuntoVisteLettura.kt:69`).
  - Sommario alone: the four lists are empty (`:27-30`).
  - "non dice che sia stato deciso o assegnato": the gate fakes the model's text, so this part cannot be proven there. It is measured on the real model per the user-decided convention: `h1.md` +268-279, 15.1 s, 0/0/0/0, "no". Proven as far as the convention allows.
- **EX-25**: `RiassuntoFattoPrimaDiR1Test.kt:19`.
  - Given: a pre-R1 `pronto` with a Sommario, all four lists, and Fonti, stored in the project file.
  - Reopened: a fresh tab shows it as not `superato`, with the same Sommario, the lists, and one Fonte chip per element (`:26-32`).
  - Regenerated: the test waits for the new Sommario and then asserts that all four lists are empty (`:34-43`).
  - Same given, when and then as the example.
- No test of another example was edited. `RiassuntoSoloSommarioTest` and the others are untouched apart from inheriting the bench's default constructor.

## Blocking findings

None.

## Advice (not blocking)

- A1 (duplication): the four `assertEquals(emptyList(), …)` lines now appear in three acceptance classes:
  - `RiassuntoSoloSommarioTest.kt:45-48`
  - `RiassuntoSoloChiacchiereTest.kt:27-30`
  - `RiassuntoFattoPrimaDiR1Test.kt:39-42`

  One `assertNessunaLista(contenuto)` in `BancoRiassunto` would hold this check once.
- A2: `RiconoscitoreCheDice` (`BancoRiassunto.kt:217-222`) takes the modulo of `frasi.size`, so an empty list fails with `ArithmeticException`. A `require(frasi.isNotEmpty())` would name the cause. It is test-only, and local recognizer doubles already exist (`AllineatorePerTurnoTest.kt:345`), so this is not a second way of doing things.
- A3: the EX-24 assertion `"meteo" in sommario && "pranzo" in sommario` (`RiassuntoSoloChiacchiereTest.kt:26`) only echoes the fake's own text. It proves that the model's Sommario is the one shown, which is acceptable, but it is weak as a content check.
