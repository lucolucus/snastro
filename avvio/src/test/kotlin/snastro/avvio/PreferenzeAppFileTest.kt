package snastro.avvio

import org.junit.jupiter.api.io.TempDir
import snastro.ui.impostazioni.Preferenze
import snastro.ui.impostazioni.TemaApp
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PreferenzeAppFileTest {
    @TempDir
    lateinit var cartella: Path

    @Test
    fun `senza file le preferenze sono quelle predefinite`() {
        assertEquals(Preferenze(), preferenzeAppFile(cartella).leggi())
    }

    @Test
    fun `cio che si salva si rilegge, anche da una nuova istanza`() {
        val salvate = Preferenze(tema = TemaApp.SCURO, cartellaProgetti = "/Volumes/Dati/progetti àè")
        preferenzeAppFile(cartella).salva(salvate)

        assertEquals(salvate, preferenzeAppFile(cartella).leggi())
        assertFalse(Files.exists(cartella.resolve("preferenze.properties.tmp")))
    }

    @Test
    fun `crea la cartella dati se manca`() {
        val annidata = cartella.resolve("a/b")
        preferenzeAppFile(annidata).salva(Preferenze(tema = TemaApp.CHIARO))

        assertEquals(TemaApp.CHIARO, preferenzeAppFile(annidata).leggi().tema)
    }

    @Test
    fun `un valore di tema sconosciuto torna a Sistema, senza perdere il resto`() {
        Files.writeString(cartella.resolve("preferenze.properties"), "tema=VIOLA\ncartellaProgetti=/x\n")

        assertEquals(Preferenze(tema = TemaApp.SISTEMA, cartellaProgetti = "/x"), preferenzeAppFile(cartella).leggi())
    }
}
