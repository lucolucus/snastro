---
id: stile-snastro
type: ui
context: ui
side: app
wave: 15
release: R2
module: ":ui (snastro.ui.stile) + ui/src/main/resources/font, ui/src/main/resources/icone"
consumes:
- kernel-pl
depends_on:
- ui-fondamenta
related_adrs:
- '0001'
- '0002'
consumes_rm: []
triggers: []
---
# stile-snastro — Stile Snastro · tema, colori, tipografia, icone, componenti condivisi

## What to do
Build the code incarnation of the approved Snastro design system in `:ui` package `snastro.ui.stile`: SnastroColori light/dark generated 1:1 from UI/design-system/tokens.json (tested against the file), SnastroTema mapping to Material 3 and following the macOS theme, SnastroMisure, bundled OFL fonts and SnastroTipografia, the 29 SVG icons with a tinted IconaSn, the new duration format, voice colours that follow the theme, and the shared components (buttons, play button, status chip, banner, fields, card, fascia meter, voice marker) each with a light+dark render-check fixture. It is the derived owner of everything both wave-16 restyle blocks share: no screen code here.

Note: DERIVED OWNER (rule 11) of the ui-kit = the design system's code incarnation, consumed by both wave-16 restyle blocks. Owns the BODY of SnastroTema (ui-fondamenta keeps the call site), Palette.kt values (AC-178 contract kept, AC-560) and formattaDurata (AC-179 duration half superseded by AC-557). Every value comes from features/trascrizione-con-parlanti/UI/design-system/tokens.json; visual targets in features/trascrizione-con-parlanti/UI/design-system/anteprime/. Views only, no presenter/command/read-model change outside AC-557/AC-560.

## Tasks
- AC-551 `SnastroColori` is a data class with one `Color` field per colour token of `UI/design-system/tokens.json` (ground, surface, raised, sunken, line, lineStrong, ink, inkMuted, inkFaint, accent, onAccent, accentHover, accentSoft, accentInk, focus, danger, dangerSoft, warning, warningSoft, voci: List<Color>(8)). Two instances `ColoriChiari` / `ColoriScuri` carry the exact hex values of the `light` / `dark` themes. A unit test parses `tokens.json` (copied to `ui/src/test/resources/design-system/tokens.json`) and asserts every field equals its token, both themes — so the Kotlin can never drift from the design system.
- AC-552 `SnastroTema(scuro: Boolean = isSystemInDarkTheme())` provides `LocalSnastroColori` and wraps `MaterialTheme` with a `ColorScheme` mapped as README §'Implementazione in Compose': primary = accent, onPrimary = onAccent, background = ground, surface = surface, surfaceContainer = raised, surfaceVariant = sunken, outline = lineStrong, outlineVariant = line, error = danger, onSurface = ink, onSurfaceVariant = inkMuted. Test: both schemes built, mapping asserted.
- AC-553 Spacing / radius / size tokens are `Dp` constants in `SnastroMisure` (space1…space7 = 4, 8, 12, 16, 24, 32, 48; radiusControl 6, radiusCard 10, radiusDialog 14, radiusPill = CircleShape; iconS 16, iconM 20, controlS 28, controlM 34, sidebar 232, pannello 320). Screens use these names, never literal dp for these roles (review criterion, and a grep in the block's test: no `RoundedCornerShape(<literal>)` outside `snastro.ui.stile`).
- AC-554 Fonts bundled as static TTF in `ui/src/main/resources/font/`: Instrument Sans (400, 500, 600), Source Serif 4 (400, 600), JetBrains Mono (500). All SIL OFL 1.1; the three `OFL.txt` files sit next to them. No network at runtime.
- AC-555 `SnastroTipografia` exposes the 11 styles of tokens.json with exact size/line-height/ weight/letter-spacing: display, title, heading, body, label, caption, overline (UPPERCASE applied by the composable, not in the string), figure (tabular figures via `fontFeatureSettings = 'tnum'`), transcript, abstract (reading family), timecode (mono, `tnum`). Material `Typography` maps bodyMedium = body, labelLarge = label, titleMedium = title, headlineSmall = display, bodySmall = caption.
- AC-556 The S5 licences list gains the three fonts (name, role 'Carattere dell'interfaccia' / 'Carattere di lettura' / 'Carattere dei tempi', licence 'SIL OFL 1.1', attribution) — through the same `LicenzaVista` list, appended by the `:avvio` composition (no `:modelli` catalogue change).
- AC-557 (supersedes AC-179's duration half; AC-203's '3:12' now matches) `formattaDurata`: under one hour `m:ss` without a leading zero on minutes (75 003 ms → '1:15', 192 000 → '3:12', 3 599 000 → '59:59'); from one hour `h:mm:ss` (3 600 000 → '1:00:00', 4 503 000 → '1:15:03'). `formattaDurataEstesa` for prose: '52 min', '1 h 04 min'. Dates unchanged ('dd/MM/yyyy'). FormattazioneTest updated to these cases; any presenter test asserting the old '01:15' form is updated in the same commit (listed in the block's report).
- AC-558 The 29 SVGs of `UI/design-system/icone/` are copied to `ui/src/main/resources/icone/`. `enum class Icona { Play, Pause, Replay, Forward, Listen, Import, Document, Reveal, Person, People, Merge, Split, Reassign, Pin, Check, Close, ChevronDown, ChevronRight, Alert, Retry, Clock, Summary, Plus, Trash, Edit, Waveform, Cube, More, Reel }` and `@Composable fun IconaSn(icona, descrizione: String?, tinta: Color = LocalContentColor.current, dimensione: Dp = iconM)` rendering the SVG (`painterResource` / SVG loader of Compose Desktop) with `ColorFilter.tint`. Test: every enum entry resolves to an existing resource (fails on a missing file).
- AC-559 No emoji and no ▶ ⏸ ■ glyphs remain as UI icons anywhere in `:ui` main sources (test greps `src/main` for `'▶'`, `'⏸'`, `'⏹'`, and U+1F300–1FAFF; string constants in `testi` such as '▶ estratto' become text 'Estratto' + `Icona.Listen`).
- AC-560 `palette(voceId)` (AC-178 keeps its contract: deterministic by number, cycle of 8) returns `LocalSnastroColori.current.voci[(n-1) mod 8]`, so it follows the theme; it becomes `@Composable` or takes `SnastroColori` explicitly (worker's choice, callers updated). PaletteTest: Voce 1..8 → the tokens voice-1..voice-8 of the active theme; Voce 9 → voice-1.
- AC-561 Voice marker: `PallinoVoce(voceId, conNome: Boolean, grande = false)` — filled 10 dp dot when the Voce has a Nome, 2 dp ring (transparent inside) when it has none. `EtichettaVoce` = dot + name (heading weight 600 in ink) or 'Voce n' (weight 500, inkMuted). The voice colour is never used for text.
- AC-562 `BottoneSn(variante: Primario|Secondario|Fantasma|Link|Pericolo, piccolo: Boolean, icona: Icona?)`: heights controlM / controlS, radiusControl; Primario = accent fill + onAccent label, hover accentHover; Secondario = raised + 1 dp lineStrong border + ink; Link = accentInk text; Pericolo = danger text. Disabled = inkFaint text, no fill. At most one Primario per screen (review criterion).
- AC-563 `BottoneIconaSn(icona, descrizione)` square controlM/controlS, tooltip = descrizione (TooltipArea), hover sunken.
- AC-564 `BottonePlay(inRiproduzione, grande: Boolean, abilitato)`: grande = 40 dp circle accent fill, Play/Pause icon 18 dp onAccent; piccolo = controlS circle accentSoft with accentInk icon 14 dp; disabled = sunken + inkFaint. Replaces every '▶'/'⏸' text button.
- AC-565 `ChipStato(tipo)` — pill 24 dp high, label 12/500, icon 14 dp: DaTrascrivere (1 dp lineStrong border, inkMuted, no icon) · InCoda(n) (sunken, Clock, 'In coda · n') · InCorso(fase, trascorso) (accentSoft, pulsing 8 dp accent dot, '<Fase> <m:ss>' with the time in timecode inkMuted) · Trascritta (no fill, Check in accentInk, inkMuted text) · NonRiuscita (dangerSoft, Alert, danger text) · Avviso(testo, icona) (warningSoft, warning text; used for 'Da identificare', 'Ritrascrizione in coda/in corso'). The pulse is 1.6 s and is disabled when the OS 'reduce motion' setting is on (if unavailable on the platform, a constant dot).
- AC-566 `BannerSn(tipo: Info|Avviso|Errore, titolo, testo, azione?)` radiusCard, fills accentSoft / warningSoft / dangerSoft, icon People|Retry|Alert in accentInk / warning / danger, title 600 + one line of body. At most one banner per screen.
- AC-567 `CampoSn` (label caption 500 above, 1 dp lineStrong border, raised fill, radiusControl, helper/error caption below; error = danger border + danger helper) and `CampoNumeroPersone` (72 dp wide, centred, tabular, placeholder 'auto'; in rows: controlS, no visible label, tooltip 'Quante persone parlano? Da 1 a 10, oppure lascia vuoto').
- AC-568 `CardSn` = raised + 1 dp line border + radiusCard + space4 padding, NO shadow. `shadow-pop` equivalent (elevation) only on menus, dialogs and the selection toolbar.
- AC-569 `MisuratoreFascia(fascia)`: two 12×6 dp pips + the word (forte = both accent, ink word; debole = first accent, inkMuted; nessuna = none, inkMuted). Never a number (INV-20).
- AC-570 Focus: every interactive component draws a 2 dp `focus` ring offset 2 dp when keyboard- focused (render-check fixture with a focused Primario and a focused field).
- AC-571 The render-check harness renders every component fixture in `ColoriChiari` and `ColoriScuri`; PNGs `build/render-check/stile-<componente>-<tema>.png`. The existing contrast check of the harness (if present) runs on both themes; otherwise a unit test asserts the contrast pairs listed in README §Colore (ink/inkMuted on surface+raised ≥ 4.5, accentInk on surface/raised/ accentSoft ≥ 4.5, onAccent on accent/accentHover ≥ 4.5, lineStrong/focus/voci on surface+raised ≥ 3) from `SnastroColori`, both themes.

## Dependencies
- ui-fondamenta — the existing screen/port whose presenter and UiStato stay untouched
- Boundaries consumed (unchanged, in-process): kernel-pl

Sources: manifest delta 2026-09-24-restyle-ui (part A), features/trascrizione-con-parlanti/UI/design-system/README.md, user decisions 2026-09-24; related_adrs 0001, 0002; tactical-model: n/a (presentation only)
