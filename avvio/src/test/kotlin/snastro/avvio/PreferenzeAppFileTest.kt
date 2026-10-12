package snastro.avvio

import org.junit.jupiter.api.io.TempDir
import snastro.ui.impostazioni.Preferenze
import snastro.ui.impostazioni.TemaApp
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PreferenzeAppFileTest {
    @TempDir
    lateinit var cartella: Path

    @Test
    fun `senza file le preferenze sono quelle predefinite`() {
        assertEquals(Preferenze(), preferenzeAppFile(cartella).leggi())
    }

    @Test
    fun `un file mancante non lascia traccia nel log`() {
        val registri = registriDi { assertEquals(Preferenze(), preferenzeAppFile(cartella).leggi()) }

        assertEquals(emptyList(), registri)
    }

    @Test
    fun `un file illeggibile da le preferenze predefinite e registra la causa`() {
        val file = cartella.resolve("preferenze.properties")
        // 0xC3 0x28 is malformed UTF-8: the reader throws MalformedInputException, a real IOException on every OS
        // and user (a directory never reaches the read, an unreadable mode is ignored by root).
        Files.write(file, byteArrayOf('t'.code.toByte(), '='.code.toByte(), 0xC3.toByte(), 0x28))

        val registri = registriDi { assertEquals(Preferenze(), preferenzeAppFile(cartella).leggi()) }

        val registro = registri.single()
        assertEquals(Level.WARNING, registro.level)
        assertTrue(registro.message.contains(file.toString()), registro.message)
        assertIs<IOException>(registro.thrown)
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

    /** Runs [blocco] with a spy on [PreferenzeAppFile]'s logger; returns the records published meanwhile. */
    private fun registriDi(blocco: () -> Unit): List<LogRecord> {
        val registri = mutableListOf<LogRecord>()
        val spia = object : Handler() {
            override fun publish(record: LogRecord) {
                synchronized(registri) { registri += record }
            }

            override fun flush() = Unit
            override fun close() = Unit
        }
        val logger = Logger.getLogger(PreferenzeAppFile::class.java.name)
        logger.addHandler(spia)
        try {
            blocco()
        } finally {
            logger.removeHandler(spia)
        }
        return synchronized(registri) { registri.toList() }
    }
}
