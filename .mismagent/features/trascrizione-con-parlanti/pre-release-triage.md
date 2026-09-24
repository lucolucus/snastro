# Pre-release triage — 2026-09-24 (HEAD db2fd34, read-only triage)

Source: every `pre-release` line of `dispatch.log` (L<n> = its line number) + the owed AC-589b follow-up.
Result: 66 OPEN · 27 FIXED · 3 OBSOLETE · 9 DECISION. Paths: `ui`=ui/src/main/kotlin/snastro/ui,
`uit`=ui/src/test/kotlin/snastro/ui, `av`=avvio/src/main/kotlin/snastro/avvio, `avt`=avvio/src/test/kotlin/snastro/avvio.

## Decisions (composer applied the recommended default; user may override)
- L518c keep the 1970 date floor · L627b S5 keeps the missing-model COUNT · L665d dropped (no code path)
- L735a keep spinners (skeleton rows deferred) · L742a keep Column (LazyColumn deferred until >~200 rows)
- L750a S3 breadcrumb stays non-clickable (sidebar covers it) · L761g checkbox label "Seleziona frase" kept
- L496b UPSERT deferred · L624a testFixtures off the app classpath deferred (before packaging)
- L706 Compose Resources migration deferred past the release (deprecation only, build risk)

## B1 · ui/stile kit + glyph test
Owns: ui/stile/{IconaSn,CampoSn,BannerSn,RiduciMovimento,BottonePlay}.kt, ui/Formattazione.kt (+FormattazioneTest),
uit/stile/{SnastroMisureTest,SenzaGlifiStileTest,+new pulse test}, uit/GlifiVietatiTest.kt, uit/registrazione/SenzaGlifiRegistrazioneTest.kt, new uit/SenzaGlifiUiTest.kt.
- L704 IconaSn parses the SVG per instance (IconaSn.kt:31) → global cache keyed (icona, density)
- L705 CampoSn label weight 400 (spec 500), no disabled look (CampoSn.kt:54,70) → caption.copy(Medium); inkFaint/sunken when disabled
- L707 radius regex misses `RoundedCornerShape(size = …)` (SnastroMisureTest.kt:43) → `\(\s*(size\s*=\s*)?[0-9]`
- L708 BannerSn body not one line (BannerSn.kt:54) → maxLines=1, Ellipsis
- L709 "0 min" under a minute, negatives unguarded (Formattazione.kt:18-36) → "< 1 min"; coerceAtLeast(0)
- L714b animated pulse never exercised → test with mainClock autoAdvance=false, riduciMovimento=false
- L723 RiduciMovimento: catch only IOException; timeout cached for the JVM; stdout not closed → catch Exception; don't cache a timeout; use {}
- L735d BottonePlay no accessible name/tooltip (BottonePlay.kt:56) → descrizione from play/pause + TooltipArea
- OWED (AC-589b) + L735c + L761e: ONE test uit/SenzaGlifiUiTest.kt walking ALL of ui/src/main (▶ ⏸ ⏹ ▾ 📌, U+1F300–1FAFF), string-aware comment stripper (does not blank `https://` in strings), asserts non-empty file list; retire SenzaGlifiStileTest, SenzaGlifiRegistrazioneTest, GlifiVietatiTest.

## B2 · ui shell + S2 + S4
Owns: ui/ShellPresenter.kt (+test), ui/registrazioni/* (Presenter, UiStato, SchermataRegistrazioni, PercorsoTrascinato), uit/registrazioni/*, ui/parlanti/SchermataParlanti.kt, a NEW avt parity test file (no existing avvio file).
- L457a Shell `avvia` stuck in Caricamento on Cancellation/Error (ShellPresenter.kt:59-76) → finally like `chiudi` (:121)
- L457b apri/crea result overwrites a newer corrente (ShellPresenter.kt:85) → on Ok use sessione.corrente.value
- L478a `rifletti` loses player state after reload (RegistrazioniPresenter.kt:132-182) → re-derive riproduzione after merge
- L478b trascorsoMs has no ticker (Presenter:258; Schermata:760) → tick 1 s while a row is InCorso
- L478e Windows `file:/C:/` mangled (PercorsoTrascinato.kt:19) → Paths.get(URI) + test
- L485a refresh-error banner sticks after a later success (Presenter:154) → separate refresh vs import error; success clears
- L485b generation guard hides an older success when a newer load fails (Presenter:111-122,322) → apply any success newer than last applied
- L485c + L755a CampoData: tests for blur-submit, Esc, blur-unchanged (RegistrazioniRigheTest)
- L485d `%+1` accepted; invalid UTF-8 → U+FFFD (PercorsoTrascinato.kt:48,54) → 2 hex digits; strict decode
- L485e riprova shows no Caricamento (Presenter:173) → set Caricamento first
- L530c CampoTitolo writes state during composition (Schermata:505) → LaunchedEffect
- L548a 1..10 range duplicated, no parity test (Presenter:551) → internal consts + avt test against NumeroPersone.di
- L548b "+4"/"04" accepted (Presenter:540) → regex `^(10|[1-9])$`
- L548c Fallita compared on motivo only (UiStato:120; Presenter:142) → also elaborazioneId
- L735b S4 empty state plain text (SchermataParlanti.kt:150) → empty-state pattern (Icona + title + line)
- L742b focus not returned to More after dropdown (SchermataParlanti.kt:275-300) → FocusRequester on dismiss
- L742c Edit has no visible edit state (SchermataParlanti.kt:328-360) → lineStrong border/underline while focused
- L742d no drop feedback when list non-empty (SchermataRegistrazioni.kt:210-240) → accentInk border/overlay while dragAttivo
- L755b AC-574 pixel check light only (RegistrazioniRigheTest.kt:61-76) → add dark run
- L755c focus lost after date edit (Schermata:554-640) → focus back to the Text after termina
- L755d same date other format not normalised (Schermata:571) → testo = formattaData(data) after parse

## B3 · ui S3 + lettore
Owns: ui/lettore/{LettorePresenter,BarraLettore}.kt, ui/registrazione/{RegistrazionePresenter,StatoVoci,SchermataPannelloVoci,SchermataRegistrazione,SchermataSomiglianza,Somiglianza}.kt,
uit/lettore/LettorePresenterTest, uit/registrazione/{RegistrazioneVociRenderCheck,RegistrazioneIdentificazione,RegistrazioneSomiglianza}Test.
- L471b document single-thread scope (LettorePresenter.kt:18-49) → KDoc
- L471c test for superseded request throwing late (LettorePresenterTest)
- L471d transient error → NonDisponibile disables retry (LettorePresenter.kt:105,114; BarraLettore.kt:73) → distinct Errore state, play enabled
- L573a two quick clicks can play out of order (RegistrazionePresenter.kt:185-230) → one Job, cancel previous
- L573b disponibile()/documento() failure → whole S3 Errore (Presenter:105,129) → degrade per call
- L573d highlight lost on pause (Presenter:164) → highlight by position
- L573f S3 riprova shows no Caricamento (Presenter:135)
- L665a ricaricaParlanti not serialised (StatoVoci.kt:138-146) → generation counter / Mutex
- L665b test: multi-segment reassign failing midway (RegistrazioneIdentificazioneTest)
- L713a AC-536 wait budget 2 s (RegistrazioneSomiglianzaTest.kt:607-617) → 10 s deadline
- L718 'occasionale' Checkbox 20 dp hit target (SchermataPannelloVoci.kt:747) → whole Row toggleable(Role.Checkbox)
- L750b AC-587 preview rows plain strings (SchermataSomiglianza.kt:144; Somiglianza.kt) → structured rows (dot + name) if the state allows without presenter-contract change
- L750c stacked 1024 layout: transcript ~150 dp (SchermataRegistrazione.kt:183-199) → weight 2:1 or min height
- L761a Cambia/Nuova persona enabled while a command is pending (SchermataPannelloVoci.kt:563-583) → enabled = haCambia
- L761b wrong KDoc + unrealistic soloLettura+unioneAbilitata fixture (Pannello:535; RenderCheckTest:841) → fixture = a pending card command (AC-414)
- L761c selected + confirmed consecutive row loses pin (SchermataRegistrazione.kt:495-503) → pin next to CasellaSelezionata
- L761d Applica/Crea coexist with a disabled Primario (Pannello:763; SchermataSomiglianza) → demote card Primario while a form/preview is open
- L761f un-solo-primario fixture misleading (RenderCheckTest:823)

## B4 · avvio main + audio + S1 port
Owns: ui/progetti/* + uit/progetti/*, av/{Main,SessioneProgettoImpl,ChiusuraAllUscita,GrafoR0}.kt, av/r1/NavigazioneProgetto.kt, av/r1+r2 ContenutoApp (picker wiring), audio/.../DecodificaFfmpeg.kt, avt/SessioneProgettoImpl*Test.
- L464d JFileChooser in a composable w/o owner (SchermataProgetti.kt:53,188,271) → `SceltaCartella` port; avvio impl via FileDialog owned by the window
- L464e/f AC-195 tautological assert (ProgettiPresenterTest.kt:165) → assert inCorso/erroreApri
- L530d S1 initial-load error under the crea form, no retry (ProgettiPresenter.kt:44) → erroreElenco + Riprova
- L530e registry queue not drained on exit (SessioneProgettoImpl.kt:525; ChiusuraAllUscita.kt) → bounded shutdown + awaitTermination
- L530f user_version 1 shows CartellaNonValida (SessioneProgettoImpl.kt:185-198) → map SchemaProgettoRifiutato → DatabasePiuRecente
- L627c window close blocks up to 3 s (Main.kt:73-76) → hide window before the bounded join
- L755e S5 from S3 → back to list, not S3 (NavigazioneProgetto.kt:35-43) → remember previous screen
- L502d/L559b rebuilt WAV not atomic (DecodificaFfmpeg.kt:31) → .tmp + ATOMIC_MOVE

## B5 · tests + docs only
Owns: .mismagent/{architetture/dev-architecture-app.md, infra-notes.md, decisions/0006}, progetto/adattatori/src/test, avt/GrafoR0Test, avt/r1/ComposizioneR1Test, avt/r2/RitrascriviR2Test, ml-sherpa RiconoscitoreSherpaModelliTest, documento LettoreNomiDaParlantiTest, parlanti/adattatori persistenza tests, new trascrizione/adattatori testFixtures helper.
- L478c function-typed collaborators → amend dev-architecture §7
- L490b infra-notes:43 and ADR 0006:30 cite verifySqlDelightMigration → reword to the current migration test
- L496a sub-ms aggiuntaAlle contract case (progetto/adattatori repo contract)
- L624b R0 guard checks import lines only (GrafoR0Test.kt:65) → match package prefixes anywhere
- L624c close() test for FALLITA (ComposizioneR1Test.kt:123)
- L627a ml-sherpa test reads per-user model cache (RiconoscitoreSherpaModelliTest.kt:80) → assumeTrue(dir exists)
- L653 TipoParlante FQN to dodge CR-1 (LettoreNomiDaParlantiTest.kt:223) → parlanti:applicazione fixture
- L692 parlanti adapter tests seed via trascritto queries (gate) → seed through an allowed helper (new trascrizione/adattatori testFixtures); don't relax the gate
- L713b AC-459 flaky once (RitrascriviR2Test.kt:120-140) → repeated runs; poll the post-commit snapshot
