# Rework 1 — stile-sintesi (reviewed head 68dc2e0)

## FAIL (verifier HIGH, also seen by the composer on the PNG)
ui/src/main/kotlin/snastro/ui/stile/FonteChip.kt:42-47: when `nome` wraps to a second line, the trailing timecode Text is pushed past
the pill's right edge and clipped (render-check stile-gruppo-fonti-chiaro/scuro.png show a stray ":"/"0" outside the chip). This
contradicts AC-S44 ("wraps to the next line instead of clipping"). `Nome` has no length cap, so real names reach this path.
**Fix:** honour AC-S44 — the chip's content must stay fully inside the pill for any name length: the timecode always visible (e.g.
constrain the name Text with weight(1f, fill = false) so the timecode keeps its intrinsic width, or cap the name with maxLines +
ellipsis if that is how AC-S44 reads — prefer keeping the full timecode visible and the chip wrapping as a whole in GruppoFonti).
Add a test that FAILS on the current code: assert the timecode node's bounds lie inside the chip's bounds for a name that wraps,
at the AC's literal 40-character name AND the long (~77-char) fixture, at 1280 and 1024 widths. Regenerate the PNGs.
Nothing else.
