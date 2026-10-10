VERDICT: PASS
SHA: 1ff4b436d2ab8db1416e777eb8f3377b51d45490
SCORES: simple=4 naming=4 modularity=4 duplication=4 concision=4 errors=5 tests=4

# Review 23-ciclo-fermato (slice, base e5a5b793)

| dimension | score | evidence |
|---|---|---|
| Simple design | 4 | `ModelloLinguisticoLlama.kt:144` one branch changed; `maxTokens(richiesta)` `:187-188` derives the bound from `MisuraRisposta.paroleSommario` (single source of the asked length); `TestiRiassunto.kt:85-97` split into the sentence and the motivo |
| Naming | 4 | `Ripetuto` / `RIPETUTO("ripetuto")` / `MESSAGGIO_MODELLO_RIPETUTO`, `TOKEN_OLTRE_IL_SOMMARIO` (`:170`) replaces the now-wrong `TOKEN_OLTRE_IL_TETTO`; bench `riassumiFinche`, `impostaLunghezzaMassima` (`BancoRiassunto.kt:69,83`) |
| Modularity | 4 | error typed in `:sintesi:applicazione` port (`ErroriApplicazioneSintesi.kt`), mapped to the domain code in the service (`EseguiProssimoRiassuntoServizio.kt:189`), worded in `:ui` only; no layer crossing |
| Duplication | 4 | one message constant used by both `messaggioPer` (`MessaggiErrore.kt:163`) and `messaggioFallimento` (`TestiRiassunto.kt:86`); 15 t/s and 600 s each appear in two test modules (advice below) |
| Concision | 4 | no dead code; old `maxTokens(Int)` removed with all callers |
| Errors | 5 | new typed error with one path: adapter -> `MotivoFallimento.RIPETUTO` -> UI sentence, each step covered by the exhaustive tables (`EseguiProssimoRiassuntoServizioTest.kt:276`, `ModelloLinguisticoFintoTest.kt:62`, `MessaggiErroreTest.kt:274`, `TestiRiassuntoTest.kt:88`) |
| Tests | 4 | EX-18 acceptance test at the presenter seam over the real composition and real adapter; unit tests named as behaviours; no real sleeps |

## Evidence run
- `MM gate`: green (`./gradlew check` + detekt). `MM check --base e5a5b793`: ok (only pre-existing ADR "no Evidence" warnings).
- `RiassuntoCicloFermatoTest` ran in this gate: 1 test, 0 failures (3.1 s). `ModelloLinguisticoLlamaTest`: 32 tests, 0 failures, the four EX-18 ones included.

## Example EX-18
Given a meeting where the model loops / when Riassumi / then failed within 600 s with "Il modello si è ripetuto: riprova." and Riprova, previous Riassunto visible.
- `RiassuntoCicloFermatoTest.kt:16-28`: previous Riassunto made, cap set to 10 000 (the spike's), fake library loops to `maxTokens` with `MAX_TOKENS`; asserts `AreaAzione.Fallito("Il modello si è ripetuto: riprova.")` (rendered with Riprova by `AreaFallito`, `SchedaRiassunto.kt:512-516`), previous Sommario still in `contenuto`, and the loop's time at 15 t/s ≤ 600 s. The assertion discriminates: with the base's cap-based bound (35 512 tokens at cap 10 000) the loop would take ≈ 2 367 s and fail.
- The bench's Trascritto is two short Segmenti (documented substitution, `BancoRiassunto.kt:35-37`), so the skatefest-sized given is carried by the unit test `ModelloLinguisticoLlamaTest.kt:166-172`: an hour of speech (10 000 words, above skatefest's 3 578) at the 10 000 cap -> 6 112 tokens ≈ 407 s + 105 s prefill/open ≤ 600 s. Together they prove the same then; nothing weakened.
- No other example's test edited. The replaced tests were `AC-S159` unit tests of the superseded bound (R1-6 in `brief.md:114`).

## Conventions
First of its kind (timing an example on the fake library at measured speed; `riassumiFinche`) is proposed in `.mismagent/conventions-proposals.md:3`, and matches the diff.

## Blocking
None.

## Advice (non-blocking)
1. ADR 0026 §5 (`decisions/0026-runtime-llm-jni-llama.md:142-143`) marks the cap-based `max_tokens` and `RispostaNonValida` MANDATORY; the diff now contradicts it. The user decision R1-6 drives the change and the builder flagged it in `design-notes.md`; the architect should amend §5/§6 and ADR 0022's code list before the release review, or the map and the code disagree.
2. `ModelloLinguisticoLlamaTest.kt:154` section header still reads "AC-S159: output bound" over tests now named EX-18.
3. `TOKEN_AL_SECONDO = 15.0` and the 600 s limit live in both `ModelloLinguisticoLlamaTest.kt:458-460` and `BancoRiassunto.kt:160` / `RiassuntoCicloFermatoTest.kt:402`; separate test modules, so tolerable, but one measured-speed constant in shared test support would keep them from drifting.
4. A legitimate Sommario longer than ~2.5x the words asked now fails as "ripetuto" instead of succeeding; the margin is argued from spike H1 (`ModelloLinguisticoLlama.kt:178-186`) but not measured on medium meetings. Worth one `benchmarkRiassunto` run on a 30-60 min sample before release.
