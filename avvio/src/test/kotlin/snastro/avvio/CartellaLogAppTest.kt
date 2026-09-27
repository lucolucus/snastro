package snastro.avvio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** AC-C87: [CartellaLogApp] is [CartellaDatiRegistroProgetti]'s own per-OS folder, plus `/log`. */
class CartellaLogAppTest {
    @Test
    fun `AC-C87 su macOS la cartella di log e sotto Library Application Support snastro log`() {
        val cartella = CartellaLogApp.risolvi(sistemaOperativo = "Mac OS X", cartellaUtente = "/Users/prova")
        assertEquals("/Users/prova/Library/Application Support/snastro/log", cartella.toString())
    }

    @Test
    fun `AC-C87 su Windows la cartella di log segue LOCALAPPDATA`() {
        val cartella = CartellaLogApp.risolvi(
            sistemaOperativo = "Windows 11",
            cartellaUtente = "C:\\Users\\prova",
            localAppData = "C:\\Users\\prova\\AppData\\Local",
        )
        assertEquals("C:\\Users\\prova\\AppData\\Local\\snastro\\log", cartella.toString())
    }

    @Test
    fun `AC-C87 su Linux la cartella di log segue XDG_DATA_HOME`() {
        val cartella = CartellaLogApp.risolvi(
            sistemaOperativo = "Linux",
            cartellaUtente = "/home/prova",
            xdgDataHome = "/home/prova/.local/share",
        )
        assertEquals("/home/prova/.local/share/snastro/log", cartella.toString())
    }

    @Test
    fun `AC-C87 la cartella di log non e mai quella del registro dei progetti, ne una cartella di progetto`() {
        val registro = CartellaDatiRegistroProgetti.risolvi(sistemaOperativo = "Linux", cartellaUtente = "/home/prova")
        val log = CartellaLogApp.risolvi(sistemaOperativo = "Linux", cartellaUtente = "/home/prova")

        assertEquals(registro.resolve("log"), log)
        assertTrue(log.isAbsolute)
        assertTrue(log != registro, "la cartella di log e' distinta da quella del registro")
    }
}
