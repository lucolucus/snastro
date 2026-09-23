package snastro.avvio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CartellaDatiRegistroProgettiTest {
    @Test
    fun `AC-348 su macOS la cartella e sotto Library Application Support`() {
        val cartella = CartellaDatiRegistroProgetti.risolvi(
            sistemaOperativo = "Mac OS X",
            cartellaUtente = "/Users/prova",
        )
        assertEquals("/Users/prova/Library/Application Support/snastro", cartella.toString())
    }

    @Test
    fun `AC-348 il rilevamento del sistema operativo usa startsWith, mai una sottostringa`() {
        val cartella = CartellaDatiRegistroProgetti.risolvi(
            sistemaOperativo = "Darwin",
            cartellaUtente = "/home/prova",
        )
        assertEquals("/home/prova/.local/share/snastro", cartella.toString())
    }

    @Test
    fun `AC-348 Windows con LOCALAPPDATA assoluto usa LOCALAPPDATA`() {
        val cartella = CartellaDatiRegistroProgetti.risolvi(
            sistemaOperativo = "Windows 11",
            cartellaUtente = "C:\\Users\\prova",
            localAppData = "C:\\Users\\prova\\AppData\\Local",
        )
        assertEquals("C:\\Users\\prova\\AppData\\Local\\snastro", cartella.toString())
    }

    @Test
    fun `AC-348 Windows senza LOCALAPPDATA la cartella e derivata dalla cartella utente`() {
        val cartella = CartellaDatiRegistroProgetti.risolvi(
            sistemaOperativo = "Windows 11",
            cartellaUtente = "C:\\Users\\prova",
            localAppData = null,
        )
        assertEquals("C:\\Users\\prova\\AppData\\Local\\snastro", cartella.toString())
    }

    @Test
    fun `AC-348 Windows con LOCALAPPDATA vuoto o solo spazi usa il fallback`() {
        val cartella = CartellaDatiRegistroProgetti.risolvi(
            sistemaOperativo = "Windows 11",
            cartellaUtente = "C:\\Users\\prova",
            localAppData = "   ",
        )
        assertEquals("C:\\Users\\prova\\AppData\\Local\\snastro", cartella.toString())
    }

    @Test
    fun `AC-348 Windows con LOCALAPPDATA relativo usa il fallback`() {
        val cartella = CartellaDatiRegistroProgetti.risolvi(
            sistemaOperativo = "Windows 11",
            cartellaUtente = "C:\\Users\\prova",
            localAppData = "AppData\\Local",
        )
        assertEquals("C:\\Users\\prova\\AppData\\Local\\snastro", cartella.toString())
    }

    @Test
    fun `AC-348 su Linux la cartella segue XDG_DATA_HOME`() {
        val cartella = CartellaDatiRegistroProgetti.risolvi(
            sistemaOperativo = "Linux",
            cartellaUtente = "/home/prova",
            xdgDataHome = "/home/prova/.local/share",
        )
        assertEquals("/home/prova/.local/share/snastro", cartella.toString())
    }

    @Test
    fun `AC-348 Linux senza XDG_DATA_HOME la cartella e derivata dalla cartella utente`() {
        val cartella = CartellaDatiRegistroProgetti.risolvi(
            sistemaOperativo = "Linux",
            cartellaUtente = "/home/prova",
            xdgDataHome = null,
        )
        assertEquals("/home/prova/.local/share/snastro", cartella.toString())
    }

    @Test
    fun `AC-348 Linux con XDG_DATA_HOME vuoto o solo spazi usa il fallback`() {
        val cartella = CartellaDatiRegistroProgetti.risolvi(
            sistemaOperativo = "Linux",
            cartellaUtente = "/home/prova",
            xdgDataHome = "   ",
        )
        assertEquals("/home/prova/.local/share/snastro", cartella.toString())
    }

    @Test
    fun `AC-348 Linux con XDG_DATA_HOME relativo usa il fallback`() {
        val cartella = CartellaDatiRegistroProgetti.risolvi(
            sistemaOperativo = "Linux",
            cartellaUtente = "/home/prova",
            xdgDataHome = "relativo/share",
        )
        assertEquals("/home/prova/.local/share/snastro", cartella.toString())
    }

    @Test
    fun `AC-348 la cartella e sempre assoluta, mai relativa a una cartella di progetto`() {
        val cartella = CartellaDatiRegistroProgetti.risolvi(sistemaOperativo = "Linux", cartellaUtente = "/home/prova")
        assertTrue(cartella.isAbsolute)
    }
}
