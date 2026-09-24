package snastro.ui.stile

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

private val GLIFI_VIETATI = setOf(0x25B6, 0x23F8, 0x23F9) // ▶ ⏸ ⏹
private val INTERVALLO_EMOJI = 0x1F300..0x1FAFF

private fun String.contieneGlifoVietato(): Boolean =
    codePoints().anyMatch { cp -> cp in GLIFI_VIETATI || cp in INTERVALLO_EMOJI }

/**
 * AC-559: no emoji, no ▶ ⏸ ⏹ glyph is used as a UI icon. Scoped to this block's own package
 * (`snastro.ui.stile`) — the design system's own code must never reach for a text glyph now that
 * [Icona]/[IconaSn] exist. The wider `:ui` main sources still render some of these glyphs directly
 * in screens (wave 16 owns them) and one `testi` constant not yet paired with its icon at the
 * call site (`SIMBOLO_FRASE_CONFERMATA` in `TestiSomiglianza.kt` → `SchermataRegistrazione.kt`'s
 * `PuntinaConfermata`) — blanking that constant here would silently remove the indicator it
 * renders, a screen concern this block does not own (see the block's report for the full list).
 */
class SenzaGlifiStileTest {
    @Test
    fun `AC-559 nessun glifo vietato o emoji in snastro-ui-stile`() {
        val radice = File("src/main/kotlin/snastro/ui/stile")
        val violazioni = radice.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contieneGlifoVietato() }
            .map { it.path }
            .toList()
        assertTrue(violazioni.isEmpty(), "Glifi vietati in snastro.ui.stile: $violazioni")
    }
}
