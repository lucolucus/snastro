package snastro.ui.stile

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * AC-554: the three OFL families are bundled as static TTF, each with its `OFL.txt` next to it —
 * no network at runtime.
 */
class SnastroCaratteriTest {
    private val fontAttesi = listOf(
        "InstrumentSans-Regular.ttf",
        "InstrumentSans-Medium.ttf",
        "InstrumentSans-SemiBold.ttf",
        "SourceSerif4-Regular.ttf",
        "SourceSerif4-SemiBold.ttf",
        "JetBrainsMono-Medium.ttf",
    )
    private val licenzeAttese = listOf(
        "InstrumentSans-OFL.txt",
        "SourceSerif4-OFL.txt",
        "JetBrainsMono-OFL.txt",
    )

    private fun risorsa(nome: String) = Thread.currentThread().contextClassLoader.getResource("font/$nome")

    @Test
    fun `AC-554 ogni font bundle esiste ed e un vero TrueType`() {
        for (nome in fontAttesi) {
            val risorsa = assertNotNull(risorsa(nome), "font mancante: $nome")
            val intestazione = risorsa.openStream().use { it.readNBytes(4) }
            // TrueType: 0x00010000, or an OpenType/TrueType-flavored font ("OTTO"/"true"/"ttcf" not expected here).
            assertTrue(intestazione.contentEquals(byteArrayOf(0, 1, 0, 0)), "$nome non e un TrueType valido")
        }
    }

    @Test
    fun `AC-554 le tre licenze OFL sono presenti accanto ai font`() {
        for (nome in licenzeAttese) {
            val risorsa = assertNotNull(risorsa(nome), "licenza mancante: $nome")
            val testo = risorsa.readText()
            val eOfl = testo.contains("SIL OPEN FONT LICENSE") || testo.contains("SIL Open Font License")
            assertTrue(eOfl, "$nome non è una OFL")
        }
    }
}
