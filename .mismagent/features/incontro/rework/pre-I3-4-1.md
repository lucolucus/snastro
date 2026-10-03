# pre-I3-4 — progetto: open I3 pre-release lines

Fix every line below (MED and LOW) in this group's modules, or, for a line you judge wrong, already fixed, or not worth fixing, say why under DECISIONS so the composer can waive it with the user. Each fix with a test where the line is about behaviour or test discrimination. Lines marked USER DECISION are decided (D-0057 in decisions.md): implement the decision as stated below, do not waive it.

Blocks involved (specs: .mismagent/features/incontro/blocks/*/done/<id>.md): aggiungi-registrazione-incontro, incontri-del-progetto, incontro, pre-I2-1, pre-I2-5

## Lines (pre-release.md line number: text)
- L230: I3 · pre-I2-1 · LOW · progetto/dominio/src/main/kotlin/snastro/progetto/dominio/OrdineDelleRegistrazioni.kt:12 · a flat S2 list shows a multi-file import's Registrazioni in reverse selection order (aggiuntaAlle desc), opposite to the Parte order; confirm the UX if a flat list is still shown · verifier · 2026-10-03
- L231: I3 · pre-I2-1 · LOW · progetto/applicazione/src/main/kotlin/snastro/progetto/applicazione/comandi/AggiungiRegistrazioneServizio.kt:67-89,120 · when commit and row check both fail, copies stay as orphans in audio/ with no later cleanup (deliberate); aggiuntaAlle up to n-1 ms ahead, can interleave with a concurrent import · verifier · 2026-10-03
- L233: I3 · pre-I2-5 · LOW · progetto/applicazione/src/main/kotlin/snastro/progetto/applicazione/porte/IncontroRepository.kt:17, progetto/adattatori/src/test/kotlin/snastro/progetto/adattatori/persistenza/IncontroRepositorySqlTest.kt:27 · salva KDoc does not state the throw for an Incontro under another Progetto; the discriminating test lives only in the SQL adapter, not the contract · verifier · 2026-10-03
