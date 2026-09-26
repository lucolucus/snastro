# Rework 1 — modello-facoltativo-avvio (reviewed head a935765)

## FAIL (verifier) — AC-S163 not proven on the BUILT graph
The only AC-S163 test (avvio/src/test/.../r1/NavigazioneProgettoTest.kt:57-87) builds ShellProgetto by hand with its own ModelliPresenter
and passes that flow itself — nothing tests the production wiring lines ContenutoAppR1.kt:66 / ContenutoAppR2.kt:82
(`etichettaModelloLinguisticoPiede = modelliPresenter.etichettaModelloLinguisticoPiede`); replacing either with MutableStateFlow(null)
stays green. "Absent otherwise (NonInstallato, Installato, Errore)" is only checked for the initial empty map.
**Fix:** use the existing built-graph fixtures (ComposizioneR1Test.kt:159 builds GrafoR1(grafoR0Di(it), ServizioModelliFinta(...), ...);
AmbienteR2.kt:160 builds GrafoR2(...)): run ContenutoAppR1(grafo, …) AND ContenutoAppR2(grafo, …) over a built graph whose
servizioModelli is a ServizioModelliFinta; drive emettiFacoltativo through InDownload → Installato, and Errore (and seed NonInstallato);
assert the footer line "Modello di linguaggio: x di y GB" is present ONLY during InDownload (found in the shell footer, not in S5).
Prove RED by temporarily replacing one wiring line with MutableStateFlow(null). Nothing else.
