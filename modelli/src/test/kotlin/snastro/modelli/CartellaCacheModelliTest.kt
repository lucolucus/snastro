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
    fun `AC-133 il rilevamento del sistema operativo usa startsWith, mai una sottostringa`() {
        // "win" e' una sottostringa di "darwin" (d-a-r-W-I-N): un controllo con `in` scambierebbe
        // Darwin per Windows. La risoluzione usa `startsWith`, mai `in` — "darwin" non inizia per
        // "mac" ne' per "windows", quindi cade nel ramo Linux/XDG (comportamento onesto: un
        // os.name imprevisto non finisce silenziosamente sul ramo sbagliato).
        val cartella = CartellaCacheModelli.risolvi(
            sistemaOperativo = "Darwin",
            cartellaUtente = "/home/prova",
        )
        assertEquals("/home/prova/.local/share/snastro/modelli", cartella.toString())
    }

    @Test
    fun `AC-334 Windows con LOCALAPPDATA assoluto usa LOCALAPPDATA, mai APPDATA`() {
        val cartella = CartellaCacheModelli.risolvi(
            sistemaOperativo = "Windows 11",
            cartellaUtente = "C:\\Users\\prova",
            localAppData = "C:\\Users\\prova\\AppData\\Local",
        )
        assertEquals("C:\\Users\\prova\\AppData\\Local\\snastro\\modelli", cartella.toString())
    }

    @Test
    fun `AC-334 Windows senza LOCALAPPDATA la cartella e derivata dalla cartella utente`() {
        val cartella = CartellaCacheModelli.risolvi(
            sistemaOperativo = "Windows 11",
            cartellaUtente = "C:\\Users\\prova",
            localAppData = null,
        )
        assertEquals("C:\\Users\\prova\\AppData\\Local\\snastro\\modelli", cartella.toString())
    }

    @Test
    fun `AC-334 Windows con LOCALAPPDATA vuoto o solo spazi usa il fallback`() {
        val cartella = CartellaCacheModelli.risolvi(
            sistemaOperativo = "Windows 11",
            cartellaUtente = "C:\\Users\\prova",
            localAppData = "   ",
        )
        assertEquals("C:\\Users\\prova\\AppData\\Local\\snastro\\modelli", cartella.toString())
    }

    @Test
    fun `AC-334 Windows con LOCALAPPDATA relativo usa il fallback`() {
        val cartella = CartellaCacheModelli.risolvi(
            sistemaOperativo = "Windows 11",
            cartellaUtente = "C:\\Users\\prova",
            localAppData = "AppData\\Local",
        )
        assertEquals("C:\\Users\\prova\\AppData\\Local\\snastro\\modelli", cartella.toString())
    }

    @Test
    fun `AC-335 su Linux la cartella segue XDG_DATA_HOME`() {
        val cartella = CartellaCacheModelli.risolvi(
            sistemaOperativo = "Linux",
            cartellaUtente = "/home/prova",
            xdgDataHome = "/home/prova/.local/share",
        )
        assertEquals("/home/prova/.local/share/snastro/modelli", cartella.toString())
    }

    @Test
    fun `AC-335 Linux senza XDG_DATA_HOME la cartella e derivata dalla cartella utente`() {
        val cartella = CartellaCacheModelli.risolvi(
            sistemaOperativo = "Linux",
            cartellaUtente = "/home/prova",
            xdgDataHome = null,
        )
        assertEquals("/home/prova/.local/share/snastro/modelli", cartella.toString())
    }

    @Test
    fun `AC-335 Linux con XDG_DATA_HOME vuoto o solo spazi usa il fallback`() {
        val cartella = CartellaCacheModelli.risolvi(
            sistemaOperativo = "Linux",
            cartellaUtente = "/home/prova",
            xdgDataHome = "   ",
        )
        assertEquals("/home/prova/.local/share/snastro/modelli", cartella.toString())
    }

    @Test
    fun `AC-335 Linux con XDG_DATA_HOME relativo usa il fallback`() {
        val cartella = CartellaCacheModelli.risolvi(
            sistemaOperativo = "Linux",
            cartellaUtente = "/home/prova",
            xdgDataHome = "relativo/share",
        )
        assertEquals("/home/prova/.local/share/snastro/modelli", cartella.toString())
    }

    @Test
    fun `AC-133 la cartella e sempre assoluta, mai relativa a una cartella di progetto`() {
        val cartella = CartellaCacheModelli.risolvi(sistemaOperativo = "Linux", cartellaUtente = "/home/prova")
        assertTrue(cartella.isAbsolute)
    }
}
