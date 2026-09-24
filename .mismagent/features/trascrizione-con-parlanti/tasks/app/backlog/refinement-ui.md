---
id: refinement-ui
type: task
side: app
release: R1 (before release) or R1.1
origin: user request 2026-09-24 after first real R1 run ("segnamoci da fare un refinement dell'interfaccia utente")
---
# Refinement dell'interfaccia utente (UI)

User feedback on R1: processing times are very good; the UI needs a refinement pass.

## To agree with the user first (style was deferred on purpose)
- density (airy vs compact), palette (keep the current purple?), a reference app;
- layout: screens are functional but bare (elements packed top-left, lots of empty space);
- the "Numero di persone" field look on the S2 row (plain fillable field, decided);
- pause icon is a colour emoji that doesn't match the ▶ glyphs (BarraLettore);
- duration format "01:15" (AC-179) vs "3:12" (AC-203 example) — pick one;
- S1 default folder proposal (~/Documents/snastro).

## Known UI items already on the pre-release list (dispatch.log "pre-release")
- S3: overlapping Segmenti all highlighted; highlight lost on pause; row ▶ not dimmed when audio is unavailable; two quick clicks can play out of order.
- S2: refresh-error banner sticks; riprova gives no feedback.
- S5: 'Mancanti' shows a count, not the model names.

## Approach
Load the artifact-design / ux guidance, propose 2–3 visual directions as renderCheck PNG mockups, let the user pick, then one ui fix-batch across S1/S2/S3/S5 + shell.

## Status 2026-09-24 [user]
Style agreed via the Design System artifact (https://claude.ai/artifact/LJek8gTjpdx6yfUY45XYc6, sources
copied to `UI/design-system/`). Superseded by `manifest-deltas/2026-09-24-restyle-ui.md`: part A (visual
restyle, approved, R2) and part B (new behaviour, to confirm). Decisions taken here: duration "m:ss" /
"h:mm:ss" (AC-557); palette soft amber; S1 default folder still open.
