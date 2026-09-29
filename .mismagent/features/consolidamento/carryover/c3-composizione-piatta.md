# Carry-overs for c3-composizione-piatta (composer)
1. D-0005: c3 finishes AC-C65.
   - Delete R0's dead `ContenutoApp` in Main.kt (never called).
   - Leave exactly ONE `ContenutoProgetto` call site. Today there are two: R1's body and r2/ContenutoProgettoR2.
   - Shell/Modelli/Progetti presenter builders stay single: PresenterCondivisi.kt, r1/ContenutoAppCondiviso.kt.
2. c1 leftovers:
   - Delete `PorteProgetto.parte` and the r2/PorteProgettoParlanti and r3/PorteProgettoSintesi sub-holders once the composition is flat.
   - Fold `DispatcherEventiInMemoria` and `registrazioni` from ContestoDatabase into PorteProgetto (ADR 0030 §1).
   - Remove the duplicate `database`/`lettura` handles, so they live only on PorteProgetto.
3. c2 leftovers (verifier LOWs):
   - Put `costruisciModelliPresenter` (r1/ContenutoAppCondiviso.kt), `costruisciShellPresenter` and `costruisciProgettiPresenter` (PresenterCondivisi.kt) in one place.
   - Drop the `@Suppress("LongParameterList")` on ContenutoProgettoR2 once CollaboratoriProgetto is typed.
   - Remove the wrong Main.kt:106 KDoc together with R0's ContenutoApp.
