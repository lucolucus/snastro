package snastro.parlanti.dominio

import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class NomeTest {
    @Test
    fun `AC-22 Nome di rifiuta il testo vuoto o fatto di soli spazi`() {
        Nome.di("").erroreAtteso<ErroreParlanti.NomeVuoto>()
        Nome.di("   \t ").erroreAtteso<ErroreParlanti.NomeVuoto>()
    }

    @Test
    fun `AC-22 normalizzato e trim piu minuscole Locale ROOT`() {
        val spaziato = Nome.di("  Marco ").atteso()
        val minuscolo = Nome.di("marco").atteso()

        assertEquals("Marco", spaziato.valore)
        assertEquals("marco", spaziato.normalizzato)
        assertEquals(spaziato.normalizzato, minuscolo.normalizzato)
        assertNotEquals(spaziato, minuscolo, "l uguaglianza segue il valore mostrato, non la chiave INV-16")
    }

    @Test
    fun `AC-22 normalizzato non dipende dalla lingua di sistema`() {
        val predefinita = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("ivo", Nome.di("IVO").atteso().normalizzato)
        } finally {
            Locale.setDefault(predefinita)
        }
    }
}
