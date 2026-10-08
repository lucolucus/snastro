package snastro.ui.testi

import snastro.ui.ErroreSessione
import snastro.ui.ProgettoEliminato
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TestiProgettiTest {
    @Test
    fun `una cartella sotto la casa dell utente si mostra con la tilde`() {
        assertEquals(
            "~/Documents/snastro/a.snastro",
            percorsoDaCasa("/Users/me/Documents/snastro/a.snastro", "/Users/me"),
        )
        assertEquals("~/a.snastro", percorsoDaCasa("/Users/me/a.snastro", "/Users/me/"))
    }

    @Test
    fun `una cartella fuori dalla casa, o una casa sconosciuta, si mostra com e`() {
        assertEquals("/Users/me2/a.snastro", percorsoDaCasa("/Users/me2/a.snastro", "/Users/me"))
        assertEquals("/Volumes/Disco/a.snastro", percorsoDaCasa("/Volumes/Disco/a.snastro", "/Users/me"))
        assertEquals("/Users/me/a.snastro", percorsoDaCasa("/Users/me/a.snastro", ""))
    }

    @Test
    fun `un progetto eliminato del tutto non lascia avvisi`() {
        assertNull(avvisoEliminazione(ProgettoEliminato.Completamente, "/Users/me"))
    }

    @Test
    fun `la conferma di eliminazione promette solo i contenuti di snastro (EX-9)`() {
        assertEquals(
            "Vengono cancellati per sempre i contenuti del progetto gestiti da snastro, comprese registrazioni, " +
                "trascrizioni e riassunti. Gli altri file nella cartella restano.",
            MESSAGGIO_CONFERMA_ELIMINA_PROGETTO,
        )
    }

    @Test
    fun `una cartella non trovata si dice col suo percorso dalla casa (EX-13)`() {
        val nonTrovata = ErroreSessione.CartellaNonTrovata("/Users/me/Documents/snastro/test.snastro")

        assertEquals("Cartella non trovata: ~/Documents/snastro/test.snastro", messaggioPer(nonTrovata, "/Users/me"))
        assertEquals("Cartella non trovata: /Users/me/Documents/snastro/test.snastro", messaggioPer(nonTrovata))
    }

    @Test
    fun `gli altri errori si dicono come sempre, con o senza la casa`() {
        assertEquals(
            messaggioPer(ErroreSessione.ProgettoGiaAperto),
            messaggioPer(ErroreSessione.ProgettoGiaAperto, "/Users/me"),
        )
    }
}
