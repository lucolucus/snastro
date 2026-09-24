package snastro.ui.stile

import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SnastroMisureTest {
    @Test
    fun `AC-553 le costanti di spaziatura corrispondono ai token`() {
        assertEquals(4.dp, SnastroMisure.space1)
        assertEquals(8.dp, SnastroMisure.space2)
        assertEquals(12.dp, SnastroMisure.space3)
        assertEquals(16.dp, SnastroMisure.space4)
        assertEquals(24.dp, SnastroMisure.space5)
        assertEquals(32.dp, SnastroMisure.space6)
        assertEquals(48.dp, SnastroMisure.space7)
    }

    @Test
    fun `AC-553 raggi e dimensioni corrispondono ai token`() {
        assertEquals(6.dp, SnastroMisure.radiusControl)
        assertEquals(10.dp, SnastroMisure.radiusCard)
        assertEquals(14.dp, SnastroMisure.radiusDialog)
        assertEquals(16.dp, SnastroMisure.iconS)
        assertEquals(20.dp, SnastroMisure.iconM)
        assertEquals(28.dp, SnastroMisure.controlS)
        assertEquals(34.dp, SnastroMisure.controlM)
        assertEquals(232.dp, SnastroMisure.sidebar)
        assertEquals(320.dp, SnastroMisure.pannello)
    }

    /**
     * AC-553 (mechanical half of the review criterion): no `RoundedCornerShape(<literal>)` outside
     * `snastro.ui.stile`. Scoped by exception to one pre-existing line this block does not own
     * (`SchermataPannelloVoci.kt`'s `RoundedCornerShape(2.dp)`, wave-16 screen territory — see the
     * block's report); every other file must use [SnastroMisure]'s named radii.
     */
    @Test
    fun `AC-553 nessun RoundedCornerShape con valore letterale fuori da snastro-ui-stile`() {
        val eccezioni = setOf("src/main/kotlin/snastro/ui/registrazione/SchermataPannelloVoci.kt")
        val pattern = Regex("""RoundedCornerShape\(\s*[0-9]""")
        val radice = File("src/main/kotlin")
        val violazioni = radice.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.path.replace('\\', '/').contains("/snastro/ui/stile/") }
            .filterNot { file -> eccezioni.any { file.path.replace('\\', '/').endsWith(it) } }
            .filter { pattern.containsMatchIn(it.readText()) }
            .map { it.path }
            .toList()
        assertTrue(violazioni.isEmpty(), "Letterali RoundedCornerShape fuori da snastro.ui.stile: $violazioni")
    }
}
