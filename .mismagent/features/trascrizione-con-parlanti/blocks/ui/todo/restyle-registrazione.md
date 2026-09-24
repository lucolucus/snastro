---
id: restyle-registrazione
type: ui
context: ui
side: app
wave: 16
release: R2
module: ":ui (snastro.ui.registrazione, snastro.ui.lettore)"
consumes:
- kernel-pl
- tec-lettore-audio
- tec-shell-ui
depends_on:
- stile-snastro
- schermata-registrazione
- schermata-registrazione-identificazione
- lettore-audio
related_adrs:
- '0017'
- '0018'
- '0019'
consumes_rm: []
triggers: []
---
# restyle-registrazione — Restyle · S3 Registrazione (intestazione, lettore con corsie delle voci, trascritto, pannello Voci, somiglianza)

## What to do
Restyle S3 Registrazione with the stile-snastro components to match UI/design-system/anteprime/ScreenRecording.html and ScreenIdentify.html: header, player bar with voice lanes drawn from the segments already in state, transcript segments in the reading face with playing/selected/confirmed states, selection toolbar, Voci panel cards, menus, the Riassegna per somiglianza card and its preview. Views only; every ADR 0017/0018/0019 behaviour and text stays.

Note: Views only. Voice lanes read the segments already in RegistrazioneUiStato (no new source). Seek/rate/clickable lanes are PART B (B5, to confirm) — in part A Replay/Forward/rate appear only if AzioniLettore already supports them. The Riassunto tab is PART B (B3) — part A shows the Voci tab only. The user approves the wave-16 render-check PNGs before merge.

## Tasks
- AC-579 Player bar (replaces BarraLettore's look; `LettorePresenter` unchanged): raised card, radiusCard, line border; transport = BottoneIcona piccolo Replay ('Indietro di 10 secondi') · BottonePlay grande · BottoneIcona piccolo Forward ('Avanti di 10 secondi'); current time in timecode ink; scrubber = 4 dp sunken track + accent played part + 14 dp raised knob with 2 dp accentInk ring; voice lanes: an 8 dp strip above the track with one rounded rect per Segmento, x = inizioMs/durataMs, width = (fineMs−inizioMs)/durataMs (min 1 dp), colour = palette(voceId); data = the segments already in `RegistrazioneUiStato` (no new source). Without segments (S2 R0 context, no transcript) the strip is not drawn. Duration + a '1×' rate chip only if the lettore port already supports rates — otherwise no chip (no new port in part A). Replay/Forward: only if `AzioniLettore` already has seek; otherwise they are omitted in part A and added by B5. Audio missing = controls at 45 % opacity, disabled, + caption in `warning` with Alert under the bar.
- AC-580 Header: caption breadcrumb 'Registrazioni ›' (click = back, existing navigation), title in `display`, caption meta '<data> · <durata estesa> · <n persone[, k da identificare]>' (from state already present), actions right: Secondario 'Apri documento' (Document) + BottoneIcona Reveal ('Mostra nella cartella') + BottoneIcona More (Ritrascrivi, where offered).
- AC-581 Layout: header, player full width, then a two-column split: transcript (flexible, text max 68 ch ≈ 640 dp) and the right panel 320 dp. At window width < 1100 dp the panel stacks under the transcript (render-check at 1024×640 must show no clipped panel).
- AC-582 Transcript segment: 56 dp right-aligned timecode gutter (inkMuted; accentInk when playing) · then 'who' line (PallinoVoce + name 13/600 or 'Voce n' 13/500 inkMuted, Pin icon 14 dp accentInk on a confirmed Segmento with the existing tooltip) · text in `transcript` style. Consecutive Segmenti of the same Voce hide the who line. Hover = sunken; playing = accentSoft; selected = accentSoft + 1.5 dp accentInk inset border, and a 16 dp checked box (accent fill, onAccent Check) replaces the timecode. Rows radiusControl, padding 10/12 dp.
- AC-583 Selection toolbar = raised + line border + elevation (shadow-pop), radiusCard: 'n frasi di <Voce>' (500) left, then Secondario piccoli with icons: 'Dai un nome a questa frase' (Person + ChevronDown), 'Riassegna a' (Reassign + ChevronDown), 'Dividi voce' (Split), 'Togli conferma' (Pin), BottoneIcona Close 'Deseleziona'. Enabling/disabling rules unchanged.
- AC-584 Right panel header: `Tabs` segmented control with a single tab 'Voci' in part A (the 'Riassunto' tab is B3) showing 'Voci · k da identificare' when k > 0; below it the 'Riassegna per somiglianza' CardSn (title heading + Secondario 'Calcola' or the running/preview states, references caption, disabled hint as caption) — texts and rules of ADR 0019 unchanged.
- AC-585 Voice card = `anteprime/VoiceCard.html`: CardSn; head = EtichettaVoce + speaking time caption (sum of the Voce's Segmenti durations — view arithmetic on state) + Fantasma piccolo 'Estratto' (Listen); unnamed with proposal: overline 'Sembra', candidate rows (name heading, type caption, MisuratoreFascia, BottoneIcona Listen), actions: Primario piccolo 'È <Nome del primo>' (Check) — label change of 'Conferma', same command — · Secondario 'Altri' (ChevronDown) · Secondario 'Nuova persona' (Plus) — label of 'nuovo…' — · Link 'Salta'; first recording: caption hint + Primario 'Dai un nome' + Link 'Salta'; named: compact head only + BottoneIcona More (Cambia, Unisci con…). Merge proposal banner = BannerSn Info inside the panel.
- AC-586 Menus (Riassegna a, Altri, Unisci con, Dai un nome) = `anteprime/Menu.html`: raised, radiusDialog, elevation, items 32 dp with EtichettaVoce + type caption right, 'Nuova persona…' after a divider.
- AC-587 Reassign preview = `anteprime/ReassignPreview.html`: heading 'Sposterò N frasi, M incerte restano dove sono', one row per move 'EtichettaVoce(da) › EtichettaVoce(a)  n' (n tabular, right), references caption, Secondario 'Annulla' + Primario 'Applica'. Computing = ProgressBar + 'Confronto le frasi… n di N' timecode.
- AC-588 Read-only banner during a re-run = BannerSn Avviso with the ADR 0018 texts; pending command rows keep the ADR 0017 texts with a caption style.
- AC-589 render-check: `ScreenRecording`-equivalent fixtures (named + one unnamed Voce, one playing, one confirmed, one selected; panel with a proposal; preview open) at 1280×800 and 1024×640, light and dark. The composer compares them with `anteprime/ScreenRecording.html` / `ScreenIdentify.html` and shows them to the user before merge.
- STATES: loading (skeleton = sunken bars), audio missing (AC-217/403 texts), read-only during a re-run (AC-452/454), pending command (ADR 0017), similarity computing / preview / changed-transcript error (ADR 0019 (b).2) keep their ACs and texts, rendered with the new components; all existing presenter tests stay green unmodified
- "AC-589b No emoji and no ▶ ⏸ ⏹ glyphs (nor U+1F300–1FAFF) remain in snastro.ui.registrazione, snastro.ui.lettore and testi/TestiSomiglianza.kt (SIMBOLO_FRASE_CONFERMATA → IconaSn(Icona.Pin)); the AC-553 exception for SchermataPannelloVoci.kt RoundedCornerShape(2.dp) is removed (SnastroMisure constant); after both wave-16 blocks merge, the stile-snastro glyph test is widened to all of :ui src/main (whichever wave-16 block merges second owns the widening)"

## Dependencies
- stile-snastro — the ui-kit owner (SnastroTema, LocalSnastroColori, SnastroTipografia, SnastroMisure, IconaSn/Icona, BottoneSn, BottoneIconaSn, BottonePlay, ChipStato, BannerSn, CampoSn/CampoNumeroPersone, CardSn, MisuratoreFascia, PallinoVoce/EtichettaVoce, palette, formattaDurata/formattaDurataEstesa): use them, never re-implement a colour/size/shape
- schermata-registrazione — the existing screen/port whose presenter and UiStato stay untouched
- schermata-registrazione-identificazione — the existing screen/port whose presenter and UiStato stay untouched
- lettore-audio — the existing screen/port whose presenter and UiStato stay untouched
- Boundaries consumed (unchanged, in-process): kernel-pl, tec-lettore-audio, tec-shell-ui

Sources: manifest delta 2026-09-24-restyle-ui (part A), features/trascrizione-con-parlanti/UI/design-system/anteprime/ScreenRecording.html, ScreenIdentify.html, PlayerBar.html, TranscriptSegment.html, VoiceCard.html, SelectionToolbar.html, ReassignPreview.html, Menu.html; related_adrs 0017, 0018, 0019; tactical-model: n/a (presentation only)
