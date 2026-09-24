package snastro.ui.registrazione

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

// ▶ ⏸ ⏹ ▾ — the AC-589b glyphs plus U+1F300..1FAFF (📌 included: U+1F4CC falls inside it).
private val GLIFI_VIETATI = setOf(0x25B6, 0x23F8, 0x23F9, 0x25BE)
private val INTERVALLO_EMOJI = 0x1F300..0x1FAFF

private fun String.contieneGlifoVietato(): Boolean =
    codePoints().anyMatch { cp -> cp in GLIFI_VIETATI || cp in INTERVALLO_EMOJI }

/** Strips `//` line comments and `/* */` block comments (KDoc included) before scanning — a doc
 * comment explaining an OLD glyph-bearing label (there are several, deliberately kept as history) is
 * not a violation; only actual code (string literals) must stay clean. */
private fun String.senzaCommenti(): String =
    replace(Regex("/\\*[\\s\\S]*?\\*/"), " ").replace(Regex("//[^\n]*"), "")

/**
 * AC-589b (rework cycle 1, MED-6): no `▶ ⏸ ⏹ ▾` glyph, no emoji, anywhere in this block's own
 * `src/main` — `snastro.ui.registrazione`, `snastro.ui.lettore`, and `testi/TestiSomiglianza.kt` — once
 * comments are stripped. Scoped narrower than `SenzaGlifiStileTest` (which only ever covers
 * `snastro.ui.stile`, unaffected here) and, unlike it, comment-blind: it does not force editing every
 * doc comment in presenter/Azioni files that merely quotes an old label for context.
 */
class SenzaGlifiRegistrazioneTest {
    @Test
    fun `AC-589b nessun glifo vietato o emoji nel codice sorgente, commenti esclusi`() {
        val radiceUi = File("src/main/kotlin/snastro/ui")
        val file = listOf(
            File(radiceUi, "registrazione"),
            File(radiceUi, "lettore"),
        ).flatMap { radice -> radice.walkTopDown().filter { it.isFile && it.extension == "kt" } } +
            File(radiceUi, "testi/TestiSomiglianza.kt")
        val violazioni = file
            .filter { it.exists() }
            .filter { it.readText().senzaCommenti().contieneGlifoVietato() }
            .map { it.path }
            .toList()
        assertTrue(violazioni.isEmpty(), "Glifi vietati (commenti esclusi) in: $violazioni")
    }
}
