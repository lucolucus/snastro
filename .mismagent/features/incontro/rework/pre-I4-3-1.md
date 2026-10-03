# pre-I4-3 — ui: open I4 pre-release lines

Fix every line below (MED and LOW) in this group's modules, or, for a line you judge wrong, already fixed, or not worth fixing, say why under DECISIONS so the composer can waive it. Each fix with a test where the line is about behaviour or test discrimination. Touch only the files the lines name (plus their tests); other groups are building in parallel on the modules listed under 'Do not touch'. Gate: `./gradlew check` green.

Blocks involved (specs: .mismagent/features/incontro/blocks/*/done/<id>.md): banner-proposta-tra-parti, schermata-parte, riassunto-vista-incontro, pre-I3-2, pre-I3-5

## Notes
- Do not touch StatoVoci.kt, ParlantiPresenter.kt, RegistrazioniPresenter.kt (group pre-I4-5 owns them).

## Lines (pre-release.md line number: text)
- L240: I4 · pre-I3-5 · LOW · ui/src/main/kotlin/snastro/ui/testi/MessaggiErrore.kt:128 · the new PartiNonTrascritte text ("Manca la trascrizione della parte N.") is pinned by no test (MessaggiErroreTest:227 checks only presence) and is duplicated in TestiRiassunto.kt:70 — one assertEquals, or share the string · verifier, code-review (opus) · 2026-10-03
- L254: I4 · pre-I3-2 · LOW · ui/src/main/kotlin/snastro/ui/registrazione/SchermataRegistrazione.kt:486-496 · Parte switcher scroll step is a constant 96dp per tab shared with the width modifier; drifts if SchedeSn tab widths stop being uniform · verifier (sonnet) · 2026-10-03
- L256: I4 · banner-proposta-tra-parti · LOW · ui/src/main/kotlin/snastro/ui/registrazione/SchermataPannelloVoci.kt:228 · KDoc "the earlier Parte's Voce survives" should say "the Voce whose first Parte is earlier" (D-0057 pin) · verifier delta (sonnet) · 2026-10-03
