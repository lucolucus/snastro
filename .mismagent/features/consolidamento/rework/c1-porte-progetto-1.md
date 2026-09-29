# Rework 1 — c1-porte-progetto (verifier FAIL + code-review HIGH ×2, head d2527c5c)

The user decided on 2026-09-29: "non dovrei avere tre volte lo stesso repo, me ne aspetto uno solo" (I shouldn't have the same repo three times; I expect only one). AC-C60/61 stay as pinned; the declared deviation is NOT accepted.

## FAIL / HIGH 1 — AC-C60 not met
- `ParlanteRepositorySql(` and `AttribuzioneRepositorySql(` are each built at 3 sites: EstensioneR2.kt:312-313 (PorteParlanti), EstensioneR2.kt:328-329 (lettoreNomiDaParlanti) and EstensioneR3.kt:92-93.
- `RiassuntoRepositorySql(` and `LunghezzaMassimaRiassuntoRepositorySql(` are built outside PorteProgetto (EstensioneR3.kt:75-76).
- Target:
  - every `…RepositorySql(` in avvio/src/main runs once per open project;
  - `RepositorySql(` appears ONLY where the pinned AC says (PorteProgetto).
- Code-review's route, which keeps CablaggioR1Test AC-356 green (no snastro.parlanti import outside r2/r3):
  - The chain R3.apri → r2.apri → r1.apri is SYNCHRONOUS, with the same ContestoEstensione. So a per-contesto memo gives one instance each.
  - For example, a Parlanti/Sintesi holder cached once on ContestoEstensione, or a typed slot PorteProgetto exposes and r2/r3 fill lazily from their own packages.
  - This preserves the ADR 0023 §1 / AC-S145 order, and needs no late binding. The worker's "real race" claim is not true.
- If "only in PorteProgetto" literally cannot hold without breaking AC-356, STOP and return BOUNCED with the exact conflict (do not narrow the AC yourself). A per-context sub-holder that PorteProgetto owns is acceptable.

## HIGH 2 — AC-C61 has no test; AC-C60's counting probe replaced by a 4-name grep
- Add a counting probe in the composition test Ambiente (AmbienteR3), as AC-C60 pins it: every repository constructor, CatalogoRegistrazioni and RigenerazioneDocumentoPolitica is constructed exactly once per open.
- Add an AC-C61 identity test: R1, R2 and R3 consumers get the === instance of each repository, of CatalogoRegistrazioni and of RigenerazioneDocumentoPolitica. This includes R1's Documento worker vs R2's PuliziaDerivatiFile, and S2 vs Sintesi's LettoreTrascrittoDaTrascrizione for StatiElaborazione.
- The AC-C60 grep must cover every `RepositorySql(`, not 4 names.

## Also fix while there (from the code-review, cheap)
- GrafoR0Test.kt:61: narrow the "porte" exemption to what PorteProgetto actually imports, not the whole package from all five roots.
- Correct the KDoc claims ("R3 reuses R2's", "real race") in PorteProgetto.kt / EstensioneR2.kt:321-322 / EstensioneR3.kt:84.
