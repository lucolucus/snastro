# Manifest delta — UI restyle to the Snastro design system (R2, user decision 2026-09-24)
This is the authoritative input for /mismagent:build-manifest. Fold it into building-blocks.yaml and
regenerate the rich block files.

**Source of truth for every visual value:** `UI/design-system/` (copied from the published Design
System artifact https://claude.ai/artifact/LJek8gTjpdx6yfUY45XYc6, version 9):
- `tokens.json` — colours (light + dark), type scale, spacing, radii, sizes. **The only place a
  colour, size or radius comes from.** A value not in it is a bug.
- `README.md` — usage rules (which token where, tone, number/time formats, iconography, Compose mapping).
- `momenti.md` — what the user sees at each moment (drives part B).
- `icone/*.svg` — the 29 icons (24×24, stroke 1.75, ink #1c1d1f; tinted at use).
- `anteprime/<Component>.html|.md` — a static HTML rendition + guideline per component and 5 screen
  mockups (`Screen*.html`). Open them in a browser with `riferimento-web.css` to see the target.
  They are the visual reference, not code to port line by line.

**User decisions 2026-09-24 [user]:** layout is broadly right, the look is not ("le cose sono disposte
bene ma sono brutte"); redo the player icons; palette = soft amber accent, warm greys, crimson errors,
steel-blue warnings, voice palette kept except Voce 2 → olive; the design system above is approved.
"Il resto procedi" → **part A is approved for build now**. Part B is specified here in detail but each
item still needs the user's go (marked `[to confirm]`).

New ACs are numbered from **AC-551** (current max AC-550, plus AC-155bis and AC-186bis).
Release: **R2**. Nothing here touches a domain, application or adapter module: `:ui` and the
`:avvio` composition only.

---

## PART A — Restyle (visual only, approved) · AC-551…AC-589

Rule for the whole part: **presenters, UiStato types, commands and read-models do not change**,
except where an AC below says so explicitly (A-exceptions: AC-557 duration format, AC-560 voice
colours, AC-579 lanes read from segments already in `RegistrazioneUiStato`). Every existing
presenter test stays green untouched; view-level texts stay those of `snastro.ui.testi`.

### NEW BLOCK stile-snastro (ui, `:ui` package `snastro.ui.stile`, wave 15, R2)
Owner of everything the screens share visually. Replaces the body of `SnastroTema` (ui-fondamenta keeps
the call site; rule 11) and `Palette.kt` / `formattaDurata` values.
depends_on: ui-fondamenta · related_adrs: 0001, 0002 · consumes: kernel-pl

**Tokens → Kotlin**
- **AC-551** `SnastroColori` is a data class with one `Color` field per colour token of
  `UI/design-system/tokens.json` (ground, surface, raised, sunken, line, lineStrong, ink, inkMuted,
  inkFaint, accent, onAccent, accentHover, accentSoft, accentInk, focus, danger, dangerSoft, warning,
  warningSoft, voci: List<Color>(8)). Two instances `ColoriChiari` / `ColoriScuri` carry the exact
  hex values of the `light` / `dark` themes. A unit test parses `tokens.json` (copied to
  `ui/src/test/resources/design-system/tokens.json`) and asserts every field equals its token, both
  themes — so the Kotlin can never drift from the design system.
- **AC-552** `SnastroTema(scuro: Boolean = isSystemInDarkTheme())` provides `LocalSnastroColori` and
  wraps `MaterialTheme` with a `ColorScheme` mapped as README §"Implementazione in Compose":
  primary = accent, onPrimary = onAccent, background = ground, surface = surface,
  surfaceContainer = raised, surfaceVariant = sunken, outline = lineStrong, outlineVariant = line,
  error = danger, onSurface = ink, onSurfaceVariant = inkMuted. Test: both schemes built, mapping asserted.
- **AC-553** Spacing / radius / size tokens are `Dp` constants in `SnastroMisure`
  (space1…space7 = 4, 8, 12, 16, 24, 32, 48; radiusControl 6, radiusCard 10, radiusDialog 14,
  radiusPill = CircleShape; iconS 16, iconM 20, controlS 28, controlM 34, sidebar 232, pannello 320).
  Screens use these names, never literal dp for these roles (review criterion, and a grep in the
  block's test: no `RoundedCornerShape(<literal>)` outside `snastro.ui.stile`).

**Typography**
- **AC-554** Fonts bundled as static TTF in `ui/src/main/resources/font/`: Instrument Sans (400, 500,
  600), Source Serif 4 (400, 600), JetBrains Mono (500). All SIL OFL 1.1; the three `OFL.txt` files
  sit next to them. No network at runtime.
- **AC-555** `SnastroTipografia` exposes the 11 styles of tokens.json with exact size/line-height/
  weight/letter-spacing: display, title, heading, body, label, caption, overline (UPPERCASE applied by
  the composable, not in the string), figure (tabular figures via `fontFeatureSettings = "tnum"`),
  transcript, abstract (reading family), timecode (mono, `tnum`). Material `Typography` maps
  bodyMedium = body, labelLarge = label, titleMedium = title, headlineSmall = display, bodySmall = caption.
- **AC-556** The S5 licences list gains the three fonts (name, role "Carattere dell'interfaccia" /
  "Carattere di lettura" / "Carattere dei tempi", licence "SIL OFL 1.1", attribution) — through the
  same `LicenzaVista` list, appended by the `:avvio` composition (no `:modelli` catalogue change).

**Formats**
- **AC-557** (supersedes AC-179's duration half; AC-203's "3:12" now matches) `formattaDurata`:
  under one hour `m:ss` without a leading zero on minutes (75 003 ms → "1:15", 192 000 → "3:12",
  3 599 000 → "59:59"); from one hour `h:mm:ss` (3 600 000 → "1:00:00", 4 503 000 → "1:15:03").
  `formattaDurataEstesa` for prose: "52 min", "1 h 04 min". Dates unchanged ("dd/MM/yyyy").
  FormattazioneTest updated to these cases; any presenter test asserting the old "01:15" form is
  updated in the same commit (listed in the block's report).

**Icons**
- **AC-558** The 29 SVGs of `UI/design-system/icone/` are copied to `ui/src/main/resources/icone/`.
  `enum class Icona { Play, Pause, Replay, Forward, Listen, Import, Document, Reveal, Person, People,
  Merge, Split, Reassign, Pin, Check, Close, ChevronDown, ChevronRight, Alert, Retry, Clock, Summary,
  Plus, Trash, Edit, Waveform, Cube, More, Reel }` and `@Composable fun IconaSn(icona, descrizione:
  String?, tinta: Color = LocalContentColor.current, dimensione: Dp = iconM)` rendering the SVG
  (`painterResource` / SVG loader of Compose Desktop) with `ColorFilter.tint`. Test: every enum entry
  resolves to an existing resource (fails on a missing file).
- **AC-559** No emoji and no ▶ ⏸ ■ glyphs remain as UI icons anywhere in `:ui` main sources
  (test greps `src/main` for `"▶"`, `"⏸"`, `"⏹"`, and U+1F300–1FAFF; string constants in `testi`
  such as "▶ estratto" become text "Estratto" + `Icona.Listen`).

**Voice colours**
- **AC-560** `palette(voceId)` (AC-178 keeps its contract: deterministic by number, cycle of 8)
  returns `LocalSnastroColori.current.voci[(n-1) mod 8]`, so it follows the theme; it becomes
  `@Composable` or takes `SnastroColori` explicitly (worker's choice, callers updated). PaletteTest:
  Voce 1..8 → the tokens voice-1..voice-8 of the active theme; Voce 9 → voice-1.
- **AC-561** Voice marker: `PallinoVoce(voceId, conNome: Boolean, grande = false)` — filled 10 dp dot
  when the Voce has a Nome, 2 dp ring (transparent inside) when it has none. `EtichettaVoce` = dot +
  name (heading weight 600 in ink) or "Voce n" (weight 500, inkMuted). The voice colour is never
  used for text.

**Shared components** (each one: composable + render-check fixture in light AND dark; visual target
= the matching `anteprime/*.html`)
- **AC-562** `BottoneSn(variante: Primario|Secondario|Fantasma|Link|Pericolo, piccolo: Boolean,
  icona: Icona?)`: heights controlM / controlS, radiusControl; Primario = accent fill + onAccent
  label, hover accentHover; Secondario = raised + 1 dp lineStrong border + ink; Link = accentInk text;
  Pericolo = danger text. Disabled = inkFaint text, no fill. At most one Primario per screen
  (review criterion).
- **AC-563** `BottoneIconaSn(icona, descrizione)` square controlM/controlS, tooltip = descrizione
  (TooltipArea), hover sunken.
- **AC-564** `BottonePlay(inRiproduzione, grande: Boolean, abilitato)`: grande = 40 dp circle accent
  fill, Play/Pause icon 18 dp onAccent; piccolo = controlS circle accentSoft with accentInk icon 14 dp;
  disabled = sunken + inkFaint. Replaces every "▶"/"⏸" text button.
- **AC-565** `ChipStato(tipo)` — pill 24 dp high, label 12/500, icon 14 dp:
  DaTrascrivere (1 dp lineStrong border, inkMuted, no icon) · InCoda(n) (sunken, Clock, "In coda · n") ·
  InCorso(fase, trascorso) (accentSoft, pulsing 8 dp accent dot, "<Fase> <m:ss>" with the time in
  timecode inkMuted) · Trascritta (no fill, Check in accentInk, inkMuted text) · NonRiuscita
  (dangerSoft, Alert, danger text) · Avviso(testo, icona) (warningSoft, warning text; used for
  "Da identificare", "Ritrascrizione in coda/in corso"). The pulse is 1.6 s and is disabled when
  the OS "reduce motion" setting is on (if unavailable on the platform, a constant dot).
- **AC-566** `BannerSn(tipo: Info|Avviso|Errore, titolo, testo, azione?)` radiusCard, fills
  accentSoft / warningSoft / dangerSoft, icon People|Retry|Alert in accentInk / warning / danger,
  title 600 + one line of body. At most one banner per screen.
- **AC-567** `CampoSn` (label caption 500 above, 1 dp lineStrong border, raised fill, radiusControl,
  helper/error caption below; error = danger border + danger helper) and `CampoNumeroPersone`
  (72 dp wide, centred, tabular, placeholder "auto"; in rows: controlS, no visible label,
  tooltip "Quante persone parlano? Da 1 a 10, oppure lascia vuoto").
- **AC-568** `CardSn` = raised + 1 dp line border + radiusCard + space4 padding, NO shadow.
  `shadow-pop` equivalent (elevation) only on menus, dialogs and the selection toolbar.
- **AC-569** `MisuratoreFascia(fascia)`: two 12×6 dp pips + the word (forte = both accent, ink word;
  debole = first accent, inkMuted; nessuna = none, inkMuted). Never a number (INV-20).
- **AC-570** Focus: every interactive component draws a 2 dp `focus` ring offset 2 dp when keyboard-
  focused (render-check fixture with a focused Primario and a focused field).

**render-check**
- **AC-571** The render-check harness renders every component fixture in `ColoriChiari` and
  `ColoriScuri`; PNGs `build/render-check/stile-<componente>-<tema>.png`. The existing contrast check
  of the harness (if present) runs on both themes; otherwise a unit test asserts the contrast pairs
  listed in README §Colore (ink/inkMuted on surface+raised ≥ 4.5, accentInk on surface/raised/
  accentSoft ≥ 4.5, onAccent on accent/accentHover ≥ 4.5, lineStrong/focus/voci on surface+raised ≥ 3)
  from `SnastroColori`, both themes.

### NEW BLOCK restyle-shell-elenchi (ui, `:ui` shell + progetti + registrazioni + parlanti + modelli, wave 16, R2)
depends_on: stile-snastro, schermata-registrazioni, schermata-registrazioni-identificazione,
schermata-progetti, schermata-parlanti, schermata-modelli · views only.
Visual targets: `anteprime/Sidebar.html`, `RecordingRow.html`, `EmptyState.html`, `DropZone.html`,
`ScreenProject.html` (list part only — the Da fare / summary cards are part B).
- **AC-572** Shell: window ground = `ground`; left sidebar 232 dp on `ground` with a 1 dp `line`
  right border; project selector row (Reel icon in accentInk, name heading ellipsised, ChevronDown);
  nav items 34 dp (Waveform "Registrazioni", People "Parlanti") with a right-aligned count when the
  data is already in the shell state (no new query), active item = raised fill + line outline +
  accentInk icon + 600 weight; footer "Modelli pronti · tutto in locale" (Cube, caption inkMuted)
  or the S5 state in words. Content area = `surface`, padding space5 vertical / space6 horizontal.
- **AC-573** S1 Progetti: display title, list rows as `CardSn` list (name heading, "n registrazioni ·
  ultima attività" caption), "Nuovo progetto" Primario, "Apri progetto…" Secondario; empty = EmptyState
  (Reel icon, "Nessun progetto", the two actions).
- **AC-574** S2 header: project name in `display`, caption "n registrazioni · <durata estesa totale>"
  only if already computable from the rows in state (sum of durataMs — allowed view arithmetic),
  "Importa audio…" Primario with Import icon at the right.
- **AC-575** S2 rows = `anteprime/RecordingRow.html`: one bordered raised container, rows separated by
  `line`, each row = BottonePlay piccolo · (title heading ellipsised / meta caption: date, duration in
  timecode, voice dots + summary text when the R2 badge data is present) · right side by state with
  `ChipStato` + actions exactly as today's behaviour (Trascrivi + CampoNumeroPersone; In coda + Link
  "Annulla"; In corso chip; Da identificare Avviso chip; Trascritta chip + BottoneIcona More
  (Ritrascrivi lives in that menu — same command, same dialog); Non riuscita: motivo in danger
  caption in the meta line + prefilled field + Secondario "Riprova" with Retry icon). Playing row =
  accentSoft fill. The editable date keeps its behaviour but renders as caption text with an Edit
  icon on hover (no boxed field at rest).
- **AC-576** S2 empty state = DropZone large (Import 28 dp, "Trascina qui un file audio", "M4A, MP3,
  WAV, FLAC · oppure" + "Scegli file…"); while a drag is over the window the drop zone style `over`
  (accentInk dashed border, accentSoft fill). Errors of import in a BannerSn Errore.
- **AC-577** S4 Parlanti: sections "Ricorrenti" / "Occasionali" as overline headers; each person row
  = PallinoVoce-like neutral dot (no voice colour: people have none outside a recording — use
  inkMuted ring) + name heading + caption (impronte, registrazioni, ultima apparizione) + BottonePlay
  piccolo + BottoneIcona Edit / More (Promuovi, Elimina…). Elimina dialog = `anteprime/Dialog.html`
  with Pericolo primary.
- **AC-578** S5 Modelli: CardSn with total size + Primario "Scarica"; per-model ProgressBar (6 dp,
  sunken track, accent fill, label + "412 / 870 MB" timecode); errors as BannerSn Errore with
  "Riprova"; licences as a plain table (name, role, licence) in caption/body.

### NEW BLOCK restyle-registrazione (ui, `:ui` registrazione + lettore, wave 16, R2)
depends_on: stile-snastro, schermata-registrazione, schermata-registrazione-identificazione,
lettore-audio · views only. Visual targets: `anteprime/ScreenRecording.html`, `ScreenIdentify.html`,
`PlayerBar.html`, `TranscriptSegment.html`, `VoiceCard.html`, `SelectionToolbar.html`,
`ReassignPreview.html`, `Menu.html`.
- **AC-579** Player bar (replaces BarraLettore's look; `LettorePresenter` unchanged): raised card,
  radiusCard, line border; transport = BottoneIcona piccolo Replay ("Indietro di 10 secondi") ·
  BottonePlay grande · BottoneIcona piccolo Forward ("Avanti di 10 secondi"); current time in
  timecode ink; scrubber = 4 dp sunken track + accent played part + 14 dp raised knob with 2 dp
  accentInk ring; **voice lanes**: an 8 dp strip above the track with one rounded rect per Segmento,
  x = inizioMs/durataMs, width = (fineMs−inizioMs)/durataMs (min 1 dp), colour = palette(voceId);
  data = the segments already in `RegistrazioneUiStato` (no new source). Without segments (S2 R0
  context, no transcript) the strip is not drawn. Duration + a "1×" rate chip only if the lettore
  port already supports rates — otherwise no chip (no new port in part A). Replay/Forward: only if
  `AzioniLettore` already has seek; otherwise they are omitted in part A and added by B5.
  Audio missing = controls at 45 % opacity, disabled, + caption in `warning` with Alert under the bar.
- **AC-580** Header: caption breadcrumb "Registrazioni ›" (click = back, existing navigation),
  title in `display`, caption meta "<data> · <durata estesa> · <n persone[, k da identificare]>"
  (from state already present), actions right: Secondario "Apri documento" (Document) +
  BottoneIcona Reveal ("Mostra nella cartella") + BottoneIcona More (Ritrascrivi, where offered).
- **AC-581** Layout: header, player full width, then a two-column split: transcript (flexible,
  text max 68 ch ≈ 640 dp) and the right panel 320 dp. At window width < 1100 dp the panel stacks
  under the transcript (render-check at 1024×640 must show no clipped panel).
- **AC-582** Transcript segment: 56 dp right-aligned timecode gutter (inkMuted; accentInk when
  playing) · then "who" line (PallinoVoce + name 13/600 or "Voce n" 13/500 inkMuted, Pin icon 14 dp
  accentInk on a confirmed Segmento with the existing tooltip) · text in `transcript` style.
  Consecutive Segmenti of the same Voce hide the who line. Hover = sunken; playing = accentSoft;
  selected = accentSoft + 1.5 dp accentInk inset border, and a 16 dp checked box (accent fill,
  onAccent Check) replaces the timecode. Rows radiusControl, padding 10/12 dp.
- **AC-583** Selection toolbar = raised + line border + elevation (shadow-pop), radiusCard: "n frasi di
  <Voce>" (500) left, then Secondario piccoli with icons: "Dai un nome a questa frase" (Person +
  ChevronDown), "Riassegna a" (Reassign + ChevronDown), "Dividi voce" (Split), "Togli conferma"
  (Pin), BottoneIcona Close "Deseleziona". Enabling/disabling rules unchanged.
- **AC-584** Right panel header: `Tabs` segmented control with a single tab "Voci" in part A
  (the "Riassunto" tab is B3) showing "Voci · k da identificare" when k > 0; below it the
  "Riassegna per somiglianza" CardSn (title heading + Secondario "Calcola" or the running/preview
  states, references caption, disabled hint as caption) — texts and rules of ADR 0019 unchanged.
- **AC-585** Voice card = `anteprime/VoiceCard.html`: CardSn; head = EtichettaVoce + speaking time
  caption (sum of the Voce's Segmenti durations — view arithmetic on state) + Fantasma piccolo
  "Estratto" (Listen); unnamed with proposal: overline "Sembra", candidate rows (name heading, type
  caption, MisuratoreFascia, BottoneIcona Listen), actions: Primario piccolo "È <Nome del primo>"
  (Check) — label change of "Conferma", same command — · Secondario "Altri" (ChevronDown) ·
  Secondario "Nuova persona" (Plus) — label of "nuovo…" — · Link "Salta"; first recording: caption
  hint + Primario "Dai un nome" + Link "Salta"; named: compact head only + BottoneIcona More
  (Cambia, Unisci con…). Merge proposal banner = BannerSn Info inside the panel.
- **AC-586** Menus (Riassegna a, Altri, Unisci con, Dai un nome) = `anteprime/Menu.html`: raised,
  radiusDialog, elevation, items 32 dp with EtichettaVoce + type caption right, "Nuova persona…"
  after a divider.
- **AC-587** Reassign preview = `anteprime/ReassignPreview.html`: heading "Sposterò N frasi, M incerte
  restano dove sono", one row per move "EtichettaVoce(da) › EtichettaVoce(a)  n" (n tabular, right),
  references caption, Secondario "Annulla" + Primario "Applica". Computing = ProgressBar + "Confronto
  le frasi… n di N" timecode.
- **AC-588** Read-only banner during a re-run = BannerSn Avviso with the ADR 0018 texts; pending
  command rows keep the ADR 0017 texts with a caption style.
- **AC-589** render-check: `ScreenRecording`-equivalent fixtures (named + one unnamed Voce, one
  playing, one confirmed, one selected; panel with a proposal; preview open) at 1280×800 and 1024×640,
  light and dark. The composer compares them with `anteprime/ScreenRecording.html` /
  `ScreenIdentify.html` and shows them to the user before merge.

### Build order (part A)
wave 15: stile-snastro → wave 16: restyle-shell-elenchi ∥ restyle-registrazione. Gate unchanged
(`./gradlew check`, which includes renderCheck). The user approves the render-check PNGs of wave 16
before the merge to `feature/trascrizione-con-parlanti` (visible-release rule).

---

## PART B — New behaviour from `UI/design-system/momenti.md` · `[to confirm]` · AC-590…

Each item is independent; the user confirms them one by one. None needs a domain rule change.

### B1 · The recording page opens in every state `[to confirm]`
Today S3 opens iff a Trascritto exists (AC-450 amendment). Proposal: the row always opens the page.
- **AC-590** Registrazione without Trascritto, NON_AVVIATA / FALLITA: page = header + player with a
  **waveform** (no lanes) + CardSn "Trascrivi questa registrazione" (text of `ScreenImported.html`,
  CampoNumeroPersone with label, Primario "Trascrivi", estimate from B2 if confirmed) + CardSn "File"
  (nome file, data editable, durata, "Mostra nella cartella"). Same commands as the S2 row
  (AvviaElaborazione with numeroPersone, validation "Da 1 a 10, oppure lascia vuoto").
- **AC-591** IN_ATTESA / IN_CORSO without Trascritto: page = header + player (waveform) + CardSn
  "Trascrizione in corso" with `PhaseProgress` (4 named phases from `fase`, elapsed from
  `avviataAlle`, "circa n min rimanenti" only with B2) + "Puoi chiudere questa pagina…" text +
  "In coda dopo questa" list with Link "Annulla" for queued runs.
- Data: `stati-elaborazione` (already has stato, fase, avviataAlle, numeroPersone, posizioneInCoda);
  waveform needs a **new** read: `FormaOnda(registrazioneId, colonne: Int): FloatArray` on the
  LettoreAudio side (peak per column, computed once and cached in the project folder) — new port in
  `:ui` + adapter in `:audio`/`:avvio`. If rejected, the player shows a plain track.

### B2 · Transcription time estimate `[to confirm]`
- **AC-592** Estimate = durataMs × rapporto, rapporto = median of (durata elaborazione / durata audio)
  over the last ≤ 10 `completata` Elaborazioni of this machine (project-independent, stored in the
  per-user settings next to `RegistroProgetti`); default 0.15 before any run (R1 measured ≤ 600 s for
  60 min). Shown as "~7 min" on NON_AVVIATA rows and B1, "circa n min rimanenti" in B1 in-progress
  (estimate − elapsed, floored at "meno di un minuto"). Never shown as a percentage.

### B3 · "Riassunto" tab on the recording page `[to confirm]`
- **AC-593** Right panel tabs "Riassunto | Voci · k da identificare"; Riassunto default when k = 0,
  Voci when k > 0. Riassunto = `anteprime/RecordingSummary.html` **facts only**: Durata, Parlato
  (union of Segmento intervals), Persone (Voci count), "Chi ha parlato" = SpeakerShare (per Voce:
  name/"Voce n", m:ss, % of Parlato, sorted desc). All computed in the presenter from segments already
  in state (pure function + unit tests).
- **AC-594** The prose summary / decisions / to-dos block is **not built** until v2 (local LLM,
  product-brief "Fuori scope v1"). No placeholder text is shown in the app.

### B4 · Project home: "Da fare" + project summary `[to confirm]`
- **AC-595** Above the S2 list, two cards side by side (stacked < 1100 dp): **Da fare** = up to three
  rows (n da trascrivere → scroll/filter the list; k voci da identificare in r registrazioni → open
  the first; f non riuscite → "Riprova" on the first), each hidden at 0, the card hidden when all are 0.
  **Progetto** = Registrazioni n, Ascoltate (sum durata, estesa), Persone (ricorrenti + "+ o ospiti"),
  Presenze = per attivo Parlante "x di n" registrazioni, top 5.
- Data: counts from the S2 rows already in state; Presenze needs a **new** Parlanti read-model
  `presenze-parlanti: [{parlanteId, nome, tipo, numRegistrazioni}]` (Parlanti context owns it; S2
  presenter joins, optional source like AC-345).

### B5 · Clickable voice lanes + seek `[to confirm]`
- **AC-596** Click on the scrubber (track or lanes) seeks there; Replay/Forward seek ∓10 s; rate chip
  cycles 1× → 1,25× → 1,5× → 2×. Needs `AzioniLettore.vaiA(ms)` and `velocita(x)` on the LettoreAudio
  port (+ adapter) if absent.

---

## Pointers to update when folding
- `tasks/app/backlog/refinement-ui.md`: superseded by this delta (part A items cover its list; its
  pre-release UI bugs stay on the pre-release list).
- `UI/ux-proposal.md`: add an "Amendment 2026-09-24 (Restyle — design system)" pointing here.
- AC-179 (duration half) superseded by AC-557; AC-178 extended by AC-560.
