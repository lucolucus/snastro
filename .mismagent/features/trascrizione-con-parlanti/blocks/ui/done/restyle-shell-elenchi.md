---
id: restyle-shell-elenchi
type: ui
context: ui
side: app
wave: 16
release: R2
module: ":ui (snastro.ui shell, snastro.ui.progetti, snastro.ui.registrazioni, snastro.ui.parlanti,
  snastro.ui.modelli) + :avvio (font licences, AC-556 wiring only)"
consumes:
- kernel-pl
- tec-shell-ui
depends_on:
- stile-snastro
- schermata-progetti
- schermata-registrazioni
- schermata-registrazioni-identificazione
- schermata-parlanti
- schermata-modelli
related_adrs:
- '0003'
- '0018'
consumes_rm: []
triggers: []
---
# restyle-shell-elenchi — Restyle · shell, S1 Progetti, S2 Registrazioni, S4 Parlanti, S5 Modelli

## What to do
Restyle the shell (sidebar), S1 Progetti, S2 Registrazioni, S4 Parlanti and S5 Modelli with the stile-snastro components so they match UI/design-system/anteprime (Sidebar, RecordingRow, EmptyState, DropZone, the list part of ScreenProject). Views only: presenters, UiStato, commands and read-models do not change; same behaviour, new look. Append the three font licences to the S5 list via the :avvio composition.

Note: Views only (rule: presenters, UiStato, commands and read-models unchanged). Same commands and dialogs as today; label changes only where the delta says so. The Da fare / project summary cards of the S2 mockup are PART B (B4, to confirm) — explicit cut. The user approves the wave-16 render-check PNGs before merge.

## Tasks
- AC-572 Shell: window ground = `ground`; left sidebar 232 dp on `ground` with a 1 dp `line` right border; project selector row (Reel icon in accentInk, name heading ellipsised, ChevronDown); nav items 34 dp (Waveform 'Registrazioni', People 'Parlanti') with a right-aligned count when the data is already in the shell state (no new query), active item = raised fill + line outline + accentInk icon + 600 weight; footer 'Modelli pronti · tutto in locale' (Cube, caption inkMuted) or the S5 state in words. Content area = `surface`, padding space5 vertical / space6 horizontal.
- AC-573 S1 Progetti: display title, list rows as `CardSn` list (name heading, 'n registrazioni · ultima attività' caption), 'Nuovo progetto' Primario, 'Apri progetto…' Secondario; empty = EmptyState (Reel icon, 'Nessun progetto', the two actions).
- AC-574 (amended 2026-09-24, D1: the project name is not in RegistrazioniUiStato — it stays in the sidebar; a display title arrives with part B4) S2 header: caption 'n registrazioni · <durata estesa totale>' only if already computable from the rows in state (sum of durataMs — allowed view arithmetic), 'Importa audio…' Primario with Import icon at the right.
- AC-575 S2 rows = `anteprime/RecordingRow.html`: one bordered raised container, rows separated by `line`, each row = BottonePlay piccolo · (title heading ellipsised / meta caption: date, duration in timecode, voice dots + summary text when the R2 badge data is present) · right side by state with `ChipStato` + actions exactly as today's behaviour (Trascrivi + CampoNumeroPersone; In coda + Link 'Annulla'; In corso chip; Da identificare Avviso chip; Trascritta chip + (amended 2026-09-24, D1: ADR 0018 [user] keeps the prefilled 'Numero di persone' field + 'Ritrascrivi' ON the row — no More menu for it); Non riuscita: motivo in danger caption in the meta line + prefilled field + Secondario 'Riprova' with Retry icon). Playing row = accentSoft fill. The editable date keeps its behaviour but renders as caption text with an Edit icon on hover (no boxed field at rest). — SUPERSEDED IN PART 2026-09-25 (ADR 0020 §6, user Q-2): in R2 (Elimina source supplied) 'Ritrascrivi' moves from the row into the row's More menu with 'Elimina…'; the prefilled field stays on the row; owned by the schermata-registrazioni rework (AC-625), not by this block
- AC-576 S2 empty state = DropZone large (Import 28 dp, 'Trascina qui un file audio', 'M4A, MP3, WAV, FLAC · oppure' + 'Scegli file…'); while a drag is over the window the drop zone style `over` (accentInk dashed border, accentSoft fill). Errors of import in a BannerSn Errore.
- AC-577 S4 Parlanti: sections 'Ricorrenti' / 'Occasionali' as overline headers; each person row = PallinoVoce-like neutral dot (no voice colour: people have none outside a recording — use inkMuted ring) + name heading + caption (impronte, registrazioni, ultima apparizione) + BottonePlay piccolo + BottoneIcona Edit / More (Promuovi, Elimina…). Elimina dialog = `anteprime/Dialog.html` with Pericolo primary.
- AC-578 S5 Modelli: CardSn with total size + Primario 'Scarica'; per-model ProgressBar (6 dp, sunken track, accent fill, label + '412 / 870 MB' timecode); errors as BannerSn Errore with 'Riprova'; licences as a plain table (name, role, licence) in caption/body.
- STATES: every existing empty / loading / error state of S1, S2, S4, S5 keeps its AC and its text, rendered with the new components (empty = EmptyState or DropZone, errors = BannerSn Errore or inline danger caption, loading = sunken placeholder rows); all existing presenter tests stay green unmodified
- "AC-578b No emoji and no ▶ ⏸ ⏹ glyphs (nor U+1F300–1FAFF) remain as UI icons in the packages this block restyles (snastro.ui shell files, progetti, registrazioni, parlanti, modelli): a test greps those src/main dirs; play/pause become BottonePlay"

## Dependencies
- stile-snastro — the ui-kit owner (SnastroTema, LocalSnastroColori, SnastroTipografia, SnastroMisure, IconaSn/Icona, BottoneSn, BottoneIconaSn, BottonePlay, ChipStato, BannerSn, CampoSn/CampoNumeroPersone, CardSn, MisuratoreFascia, PallinoVoce/EtichettaVoce, palette, formattaDurata/formattaDurataEstesa): use them, never re-implement a colour/size/shape
- schermata-progetti — the existing screen/port whose presenter and UiStato stay untouched
- schermata-registrazioni — the existing screen/port whose presenter and UiStato stay untouched
- schermata-registrazioni-identificazione — the existing screen/port whose presenter and UiStato stay untouched
- schermata-parlanti — the existing screen/port whose presenter and UiStato stay untouched
- schermata-modelli — the existing screen/port whose presenter and UiStato stay untouched
- Boundaries consumed (unchanged, in-process): kernel-pl, tec-shell-ui

Sources: manifest delta 2026-09-24-restyle-ui (part A), features/trascrizione-con-parlanti/UI/design-system/anteprime/Sidebar.html, RecordingRow.html, EmptyState.html, DropZone.html, ScreenProject.html (list part); related_adrs 0003, 0018; tactical-model: n/a (presentation only)
