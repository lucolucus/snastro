# pre-I3-2 — ui: open I3 pre-release lines

Fix every line below (MED and LOW) in this group's modules, or, for a line you judge wrong, already fixed, or not worth fixing, say why under DECISIONS so the composer can waive it with the user. Each fix with a test where the line is about behaviour or test discrimination. Lines marked USER DECISION are decided (D-0057 in decisions.md): implement the decision as stated below, do not waive it.

Blocks involved (specs: .mismagent/features/incontro/blocks/*/done/<id>.md): banner-proposta-tra-parti, schermata-parte, pannello-voci-incontro, pre-I2-7

## User decision (D-0057, already implemented in parlanti by pre-I3-1)
- parteA/parteB now name the Parte the ▶ estratto plays from: check the banner copy ("Voce 5 e Voce 1 (parte 1) sembrano la stessa persona") reads correctly with that meaning; adjust copy/tests only if needed.
L236 "Riprovala dall'elenco": confirm with a test (or a render check) that S2 offers a retry for a failed Parte; if it does not, report it under DEVIATIONS (do not invent a new flow).

## Lines (pre-release.md line number: text)
- L189: I3 · banner-proposta-tra-parti · MED · ui/src/main/kotlin/snastro/ui/registrazione/StatoVoci.kt:488,189-190 · after a Revisione the superseded tra-parti job is cancelled only after the reload read: an in-flight pre-join pair can be published briefly; cancel lavoroTraParti next to coppiaTraParti = null · verifier · 2026-10-02
- L191: I3 · banner-proposta-tra-parti · LOW · ui/src/main/kotlin/snastro/ui/stile/BannerSn.kt:55, ui/src/main/kotlin/snastro/ui/registrazione/SchermataPannelloVoci.kt:230-250 · empty body Text laid out; ▶ buttons lack a contentDescription; a Cambiamento storm re-runs the computation (relies on the cache) · verifier · 2026-10-02
- L235: I3 · pre-I2-7 · MED · ui/src/main/kotlin/snastro/ui/registrazione/SchermataRegistrazione.kt:~481 · the Parte switcher scrolls but does not bring the selected Parte into view (Parte 10-12 of 12 shows no highlighted tab until scrolled) · verifier · 2026-10-03
- L236: I3 · pre-I2-7 · LOW · ui/src/main/kotlin/snastro/ui/testi/TestiRegistrazione.kt:~48, ui/src/test/kotlin/snastro/ui/registrazioni/RegistrazioniRenderCheckTest.kt:~1208, ui/src/main/kotlin/snastro/ui/registrazione/RegistrazionePresenter.kt:~213 · "Riprovala dall'elenco" — confirm S2 offers a retry for a failed Parte (release smoke); INV-I3 SHA is host-dependent (pixel diff); statoSenzaTrascritto reads the full incontri() list · verifier · 2026-10-03
- (added 2026-10-03 from the pre-I3-1 review, MED) ui/src/main/kotlin/snastro/ui/testi/TestiVoci.kt:73 · KDoc still says [parteA] is the first Parte of voce A; with D-0057 (b) and the pin at building-blocks.yaml:1482 it is the Parte the estratto plays from — fix the KDoc and check the banner copy against it · code-review, verifier (opus) · 2026-10-03
