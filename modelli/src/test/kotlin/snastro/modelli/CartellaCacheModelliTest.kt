package snastro.modelli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CartellaCacheModelliTest {
    @Test
    fun `AC-133 su macOS la cartella e sotto Library Application Support`() {
        val cartella = CartellaCacheModelli.risolvi(
            sistemaOperativo = "Mac OS X",
            cartellaUtente = "/Users/prova",
        )
        assertEquals("/Users/prova/Library/Application Support/snastro/modelli", cartella.toString())
    }

    @Test
    fun `AC-133 su Windows la cartella segue APPDATA`() {
        val cartella = CartellaCacheModelli.risolvi(
            sistemaOperativo = "Windows 11",
            cartellaUtente = "C:\\Users\\prova",
            appData = "C:\\Users\\prova\\AppData\\Roaming",
        )
        assertEquals("C:\\Users\\prova\\AppData\\Roaming\\snastro\\modelli", cartella.toString())
    }

    @Test
    fun `AC-133 su Windows senza APPDATA la cartella e derivata dalla cartella utente`() {
        val cartella = CartellaCacheModelli.risolvi(
            sistemaOperativo = "Windows 11",
            cartellaUtente = "C:\\Users\\prova",
            appData = null,
        )
        assertEquals("C:\\Users\\prova\\AppData\\Roaming\\snastro\\modelli", cartella.toString())
    }

    @Test
    fun `AC-133 su Linux la cartella segue XDG_DATA_HOME`() {
        val cartella = CartellaCacheModelli.risolvi(
            sistemaOperativo = "Linux",
            cartellaUtente = "/home/prova",
            xdgDataHome = "/home/prova/.local/share",
        )
        assertEquals("/home/prova/.local/share/snastro/modelli", cartella.toString())
    }

    @Test
    fun `AC-133 su Linux senza XDG_DATA_HOME la cartella e derivata dalla cartella utente`() {
        val cartella = CartellaCacheModelli.risolvi(
            sistemaOperativo = "Linux",
            cartellaUtente = "/home/prova",
            xdgDataHome = null,
        )
        assertEquals("/home/prova/.local/share/snastro/modelli", cartella.toString())
    }

    @Test
    fun `AC-133 la cartella e sempre assoluta, mai relativa a una cartella di progetto`() {
        val cartella = CartellaCacheModelli.risolvi(sistemaOperativo = "Linux", cartellaUtente = "/home/prova")
        assertTrue(cartella.isAbsolute)
    }
}
