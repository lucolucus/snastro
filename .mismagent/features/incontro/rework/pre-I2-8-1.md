# pre-I2-8 — release check: multi-Parte on the real adapters: open I2 pre-release lines

Fix every line below (MED and LOW) in this group's modules, or, for a line you judge wrong, already fixed, or not worth fixing, say why under DECISIONS so the composer can waive it with the user. Lines marked RELEASE CHECK / MUST-FIX are I2 release blockers: they must be fixed, not waived, unless they are already satisfied (then prove it with the test that covers them). Each fix with a test where the line is about behaviour or test discrimination.

Blocks involved (specs: .mismagent/features/incontro/blocks/*/done/<id>.md): feature

## Lines (pre-release.md line number: text)
- L63: I2 · feature · MED · (all D-0037 port blocks) · RELEASE CHECK for I2: every real-adapter Ambiente has its multi-Parte capability flag ON (piuPartiPerIncontro / incontriConPiuParti) with aggiungiParte implemented through the import command, the multi-Parte contract cases (AC-I18/I24/I25/I26/I27 …) pass on the real adapters, and every one-Parte fail-closed transition (check(size == 1), parteUnica, LettoreRegistrazioneDaProgetto.kt:38) is gone (LettoreVociDaTrascrizione.kt:43 and Sbobinatura LettoreTrascrittoDaTrascrizione.kt:42 removed by voci-del-trascritto-incontro) · composer · 2026-10-02
- L167 (routed from pre-I2-3): I2 · adattatori-sintesi-incontro · LOW · sintesi/adattatori/src/test (LettoreIncontroDaProgettoTest) · piuPartiPerIncontro stays false: contract multi-Parte cases need modificaOraDiInizio with progetto.dominio OraDiInizio (CR-1); needs a Progetto fixture building the command from LocalTime before I2 · worker · 2026-10-02 — needs a Progetto testFixture that builds ModificaOraDiInizio from a LocalTime so :sintesi:adattatori contract tests can flip their flag (CR-1).
