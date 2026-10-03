# pre-I3-5 — trascrizione + sbobinatura: open I3 pre-release lines

Fix every line below (MED and LOW) in this group's modules, or, for a line you judge wrong, already fixed, or not worth fixing, say why under DECISIONS so the composer can waive it with the user. Each fix with a test where the line is about behaviour or test discrimination. Lines marked USER DECISION are decided (D-0057 in decisions.md): implement the decision as stated below, do not waive it.

Blocks involved (specs: .mismagent/features/incontro/blocks/*/done/<id>.md): voci-dell-incontro, adattatori-trascrizione-incontro, adattatori-sbobinatura-incontro, pre-I2-2, pre-I2-8

L239 spans files outside trascrizione (ui MessaggiErrore.kt, two Parlanti tests, a sbobinatura test): you may touch exactly those files for L239.

## Lines (pre-release.md line number: text)
- L232: I3 · pre-I2-2 · LOW · trascrizione/applicazione/src/testFixtures/kotlin/snastro/trascrizione/applicazione/porte/VociDellIncontroRepositoryContratto.kt:177, trascrizione/adattatori/src/test/kotlin/snastro/trascrizione/adattatori/persistenza/VociDelTrascrittoLetturaCoerenteSqlTest.kt (driverDi) · fake (copia carries rimosse) and SQL differ on re-completing a removed Parte after reload (unreachable in production; pin or note it in the contract); test reaches a private field by reflection · verifier · 2026-10-03
- L239: I3 · pre-I2-8 · LOW · trascrizione/applicazione/src/testFixtures/kotlin/snastro/trascrizione/applicazione/porte/VociDellIncontroRepositoryContratto.kt:241, ui/src/main/kotlin/snastro/ui/testi/MessaggiErrore.kt:127-129, sbobinatura/adattatori/src/test/.../LettoreTrascrittoDaTrascrizioneTest.kt:576-641 · orphaned KDoc; PartiNonTrascritte message still one-Parte wording + TRANSITION comment; stale comments "I2 import does not exist yet" in two Parlanti tests; sbobinatura test ordineParti goes stale after a date/time change · verifier · 2026-10-03
