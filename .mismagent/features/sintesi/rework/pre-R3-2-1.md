# pre-R3-2-1 — pre-release cleanup, area "Interfaccia" (22 lines)

Fix EVERY line below against the CURRENT code (paths may have moved; "posizione attuale" is where the triage found it on 2026-09-29).
Each line keeps its reference (A=.mismagent/features/sintesi/pre-release.md, B=.mismagent/features/consolidamento/pre-release.md, line number).
A MED needs a test that fails without the fix; a LOW may be a direct fix. If a line turns out already fixed or impossible, say so with evidence in your return (do not silently skip).

## A113 · MED · stile-sintesi
- original: R3 · stile-sintesi · MED · ui/.../stile/FonteChip.kt:54 · weight(1f, fill=false) → under unbounded width (LazyRow/horizontalScroll) the name gets maxWidth 0 and disappears; document "requires bounded width" or use a layout not relying on weight · code-review · 2026-09-26
- posizione attuale: ui/src/main/kotlin/snastro/ui/stile/FonteChip.kt:54
- triage: weight(1f, fill=false) invariato; vincolo 'larghezza limitata' annotato solo in SchedaRiassunto.kt:99, non nella KDoc di FonteChip; nessun uso attuale in LazyRow

## A114 · MED · stile-sintesi
- original: R3 · stile-sintesi · MED · SchedeSn.kt:112-119 · tab mark (in coda / in corso) not announced by screen readers — add stateDescription per SegnoScheda · code-review · 2026-09-26
- posizione attuale: ui/src/main/kotlin/snastro/ui/stile/SchedeSn.kt:107-121
- triage: SegnoSchedaVista senza stateDescription: 'in coda'/'in corso' non annunciati

## A148 · MED · scheda-riassunto
- original: R3 · scheda-riassunto · MED · TestiRiassunto.kt:25 · LIMITE_CARATTERI_ARGOMENTO=200 duplicates Argomento.MASSIMO_CARATTERI (+ literal "200" in ERRORE_ARGOMENTO_TROPPO_LUNGO) — carry the bound in a view · verifier · 2026-09-27
- posizione attuale: ui/src/main/kotlin/snastro/ui/testi/TestiRiassunto.kt:25,29
- triage: LIMITE_CARATTERI_ARGOMENTO=200 e il testo letterale 'Al massimo 200 caratteri.' duplicano Argomento.MASSIMO_CARATTERI (KDoc: duplicato di proposito per CR-1(b))

## A149 · MED · scheda-riassunto
- original: R3 · scheda-riassunto · MED · TestiRiassunto.kt:18 · BYTE_MODELLO_LINGUISTICO hardcoded "servono 6,2 GB" (MotivoDownload drops richiestiByte) — drifts with the catalogue · verifier · 2026-09-27
- posizione attuale: ui/src/main/kotlin/snastro/ui/testi/TestiRiassunto.kt:18,60
- triage: BYTE_MODELLO_LINGUISTICO è ancora un valore fisso nel messaggio SpazioInsufficiente; MotivoDownload non porta richiestiByte

## A151 · MED · scheda-riassunto
- original: R3 · scheda-riassunto · MED · RiassuntoPresenter.kt:121-130 · concurrent reloads from 3 triggers, none cancelled — stale-last-writer; use collectLatest/single-flight · verifier · 2026-09-27
- posizione attuale: ui/src/main/kotlin/snastro/ui/riassunto/RiassuntoPresenter.kt:103-132
- triage: Tre trigger (init, cambiamenti, statoFacoltativi) lanciano ricarica() in parallelo senza cancellazione né collectLatest

## A152 · MED · scheda-riassunto
- original: R3 · scheda-riassunto · MED · SchedaRiassunto.kt:342-359 · state 6 plain text instead of StatusChip, state 7 no pulsing dot, states 1/4 not EmptyState · verifier · 2026-09-27
- posizione attuale: ui/src/main/kotlin/snastro/ui/riassunto/SchedaRiassunto.kt:364-383
- triage: Lo stato 6 (InCoda) è testo semplice, non StatusChip; lo stato 7 non ha il punto pulsante; gli stati 1/4 non usano EmptyState

## A154 · MED · scheda-riassunto
- original: R3 · scheda-riassunto · MED · RiassuntoPresenter.kt:99,196-200 · messaggioErrore cleared only by a later successful Riassumi — after a race it stays through in_attesa→in_corso→pronto; clear on the next reload/state change · verifier+code-review · 2026-09-27
- posizione attuale: ui/src/main/kotlin/snastro/ui/riassunto/RiassuntoPresenter.kt:101,196-203
- triage: messaggioErrore torna null solo dopo un Riassumi Ok; la ricarica e i cambi di stato non lo azzerano

## A155 · MED · scheda-riassunto
- original: R3 · scheda-riassunto · MED · SchedaRiassunto.kt:158 · inline error sits above the privacy line — below the fold when the area is on top (6/7/10) or content is long · verifier · 2026-09-27
- posizione attuale: ui/src/main/kotlin/snastro/ui/riassunto/SchedaRiassunto.kt:141-167
- triage: Il MessaggioTonale d'errore è ancora sotto contenuto e area azione, sopra la riga privacy

## A156 · MED · scheda-riassunto
- original: R3 · scheda-riassunto · MED · RiassuntoUiStato.kt:43-44 · Fallito outranks NonDisponibile → enabled "Riprova" certain to be refused (AC-S129 wants it disabled with caption) — possible product call · code-review · 2026-09-27
- posizione attuale: ui/src/main/kotlin/snastro/ui/riassunto/RiassuntoUiStato.kt:36-46
- triage: areaAzione mette ancora Fallito prima di NonDisponibile; nessuna decisione registrata
- USER DECISION D-0014: Riprova DISABLED with the NonDisponibile caption when the recording is not available (NonDisponibile outranks Fallito for the button).

## A157 · MED · scheda-riassunto
- original: R3 · scheda-riassunto · MED · RiassuntoPresenter.kt:210-224 · scaricaModello has no in-flight guard — double click starts two 6 GB downloads · code-review · 2026-09-27
- posizione attuale: ui/src/main/kotlin/snastro/ui/riassunto/RiassuntoPresenter.kt:212-226
- triage: scaricaModello lancia senza guardia 'in volo': un doppio clic avvia due scaricaFacoltativo

## A158 · MED · scheda-riassunto
- original: R3 · scheda-riassunto · MED · RiassuntoRoute.kt:44-59 · lifecycle: id change keeps the old presenter's coroutines; the route is recomposed on every Trascrizione↔Riassunto switch → typed Argomento / open editor lost — wrap in key(registrazioneId) and hoist state · code-review · 2026-09-27
- posizione attuale: ui/src/main/kotlin/snastro/ui/riassunto/RiassuntoRoute.kt:32-62
- triage: In produzione il presenter è issato per registrazione sullo scope di S3 (Presenter.kt:86, sintesi D-0011); RiassuntoRoute.kt resta codice morto (nessun chiamante) con lo stesso difetto

## B83 · MED · c4-presenter-obbligatori
- original: R3c · c4-presenter-obbligatori · MED · ui/src/main/kotlin/snastro/ui/registrazione/RegistrazioneUiStato.kt:74 + SchermataRegistrazioni.kt:444,513,1014 · release-flag leftovers at UI-state level: `contenutoRiassunto == null -> null` (AC-S119 variant) and StatoEliminazione.Assente reachable only from fixtures; make non-null / retire (also sintesi pre-release line 128 render-fixture clause) · verifier · 2026-09-29
- posizione attuale: ui/src/main/kotlin/snastro/ui/registrazione/RegistrazioneUiStato.kt:56,74; SchermataRegistrazione.kt:238-253; ui/.../registrazioni/RegistrazioniUiStato.kt:91,107; SchermataRegistrazioni.kt:444,513,1014
- triage: contenutoRiassunto ancora nullable con ramo 'null -> null'; StatoEliminazione.Assente raggiungibile solo da fixture

## A112 · LOW · stile-sintesi
- original: R3 · stile-sintesi · LOW · SchedeSn.kt:36-51 · tab Row lacks Modifier.selectableGroup() around Role.Tab children (a11y) · verifier · 2026-09-26
- posizione attuale: ui/src/main/kotlin/snastro/ui/stile/SchedeSn.kt:51
- triage: Row delle schede senza Modifier.selectableGroup()

## A115 · LOW · stile-sintesi
- original: R3 · stile-sintesi · LOW · SchedeSn.kt:83-90 · selectable ripple not clipped to the rounded shape; focus ring partly covered by the raised neighbour · code-review · 2026-09-26
- posizione attuale: ui/src/main/kotlin/snastro/ui/stile/SchedeSn.kt:81-94
- triage: selectable applicato prima dello shape della Surface: ripple non ritagliato; anello focus coperto dal vicino rialzato

## A116 · LOW · stile-sintesi
- original: R3 · stile-sintesi · LOW · SchermataPannelloVoci.kt:159-164 · Voci panel single tab is now a focusable no-op Role.Tab (dead keyboard stop) · code-review · 2026-09-26
- posizione attuale: ui/src/main/kotlin/snastro/ui/registrazione/SchermataPannelloVoci.kt:159-164
- triage: Pannello Voci usa SchedeSn con una sola scheda e onSeleziona={} -> fermata di tastiera inutile

## A118 · LOW · stile-sintesi
- original: R3 · stile-sintesi · LOW · GruppoFontiTest.kt:74-114 · tests don't assert the name actually wrapped; window variants redundant (fixed 300dp container); SchedeSnTest InAttesa doesn't check Icona.Clock, no reduce-motion test for InCorso · verifier+code-review · 2026-09-26
- posizione attuale: ui/src/test/kotlin/snastro/ui/stile/GruppoFontiTest.kt:74-100; SchedeSnTest.kt:55-98
- triage: GruppoFontiTest non verifica che il nome vada a capo; varianti finestra ridondanti (contenitore fisso); SchedeSnTest InAttesa non verifica Icona.Clock; nessun test riduciMovimento=true per InCorso

## A128 · LOW · schede-registrazione
- original: R3 · schede-registrazione · LOW · RegistrazioneSchedeRenderCheckTest.kt · precedence render doesn't assert the other two banners absent; tab/mark fixtures default pannello=null (R1 shape) — add one tab+mark render with the Voci panel; AC-S120 "right panel only Voci" not asserted · verifier · 2026-09-26
- posizione attuale: ui/src/test/kotlin/snastro/ui/registrazione/RegistrazioneSchedeRenderCheckTest.kt:79,112-199
- triage: Fixture render ancora con pannello=null di default; precedenza banner senza assert di assenza degli altri; AC-S120 'pannello destro solo Voci' non verificato

## A130 · LOW · schede-registrazione
- original: R3 · schede-registrazione · LOW · RegistrazionePresenter.kt:182 · contenutoRiassunto new lambda per carica() → Dati never equal, slot recomposes · verifier · 2026-09-26
- posizione attuale: ui/src/main/kotlin/snastro/ui/registrazione/RegistrazionePresenter.kt:171
- triage: contenutoRiassunto = { riassunto.contenuto(registrazioneId) } nuova lambda a ogni carica(): Dati mai uguali, lo slot si ricompone

## A153 · LOW · scheda-riassunto
- original: R3 · scheda-riassunto · LOW · RiassuntoPresenter.kt:121-122,173,191,244-254; RiassuntoRoute.kt:45 · null vista → skeleton forever; Salva no in-flight guard + 2 s timer closes a reopened editor + any Errore shows the range message; UI counts untrimmed length; remember(registrazioneId) captures first-composition lambdas · verifier · 2026-09-27
- posizione attuale: ui/src/main/kotlin/snastro/ui/riassunto/RiassuntoPresenter.kt:124,239-257; ui/src/main/kotlin/snastro/ui/riassunto/RiassuntoRoute.kt:45
- triage: vista()==null lascia lo scheletro per sempre (return a :124); salvaLunghezzaMassima senza guardia in volo; il timer di 2 s azzera anche un editor riaperto; ogni Errore mostra il testo di range; il contatore usa la lunghezza non ripulita

## A159 · LOW · scheda-riassunto
- original: R3 · scheda-riassunto · LOW · RiassuntoPresenter.kt:111-118,195-203,245-252 · 1 s ticker runs always; riassumiCmd throwing leaves invioInCorso true; any ModificaLunghezza Errore shows the range text; a11y: no liveRegion/heading()/loading semantics; AC-S139 test lacks the re-read clause; metadata line position · verifier+code-review · 2026-09-27
- posizione attuale: ui/src/main/kotlin/snastro/ui/riassunto/RiassuntoPresenter.kt:113-120,192-206,254
- triage: Il ticker da 1 s gira sempre; riassumiCmd che lancia lascia invioInCorso=true (manca try/finally); ogni Errore di ModificaLunghezza mostra il testo di range; mancano semantiche a11y

## A177 · LOW · avvio-sintesi
- original: R3 · avvio-sintesi · LOW · avvio/.../r3/GrafoR3.kt:22,25 · model id + size literal in 3 places (GrafoR3, RiassuntoPresenter companion, TestiRiassunto BYTE_MODELLO_LINGUISTICO) — drift risk; single source · verifier · 2026-09-27
- posizione attuale: ui/src/main/kotlin/snastro/ui/riassunto/RiassuntoPresenter.kt:75,286; ui/src/main/kotlin/snastro/ui/testi/TestiRiassunto.kt:18
- triage: GrafoR3 è eliminato e l'avvio legge id e dimensione dal catalogo (ModelliApp.kt:12-15). Restano però due valori fissi: il default in RiassuntoPresenter (:286) e BYTE_MODELLO_LINGUISTICO in TestiRiassunto (:18)

## A180 · LOW · avvio-sintesi
- original: R3 · avvio-sintesi · LOW · render s3-riassunto.png · Riassunto content box short/clipped (Azioni cut); Voci panel + "Riassegna per somiglianza" stay under the tabs with Riassunto selected — confirm S3 layout with the user · verifier · 2026-09-27
- posizione attuale: render s3-riassunto.png / ui SchermataRegistrazione
- triage: Nessuna decisione registrata sul layout S3 con Riassunto selezionato
- USER DECISION D-0014: fix the S3 layout: with the Riassunto tab selected, the summary gets the full content area (Voci panel / "Riassegna per somiglianza" hidden or collapsed) and nothing is clipped; update render-check fixtures accordingly.
