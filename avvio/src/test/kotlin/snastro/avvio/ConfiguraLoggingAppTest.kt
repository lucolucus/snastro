package snastro.avvio

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * AC-C87/AC-C88/AC-C89/AC-C90: [configuraLoggingApp] on its own — always over an INJECTED temp folder
 * ([TempDir]), never [cartellaLogAppReale]'s real per-user one (no test here ever touches the developer's
 * own log folder).
 */
class ConfiguraLoggingAppTest {
    private val logger = Logger.getLogger("snastro")

    /** Every test removes AND closes its own handler right away: `"snastro"` is a JVM-wide singleton logger. */
    @AfterTest
    fun pulisci() {
        logger.handlers.forEach {
            logger.removeHandler(it)
            it.close()
        }
    }

    @Test
    fun `AC-C88 con limiti piccoli iniettati la rotazione lascia esattamente 3 file, il piu vecchio sparisce`(
        @TempDir cartella: Path,
    ) {
        val limite = 500
        val handler = checkNotNull(configuraLoggingApp(cartellaLog = cartella, limiteByte = limite, numeroFile = 3)) {
            "una cartella temporanea appena creata e' sempre scrivibile"
        }
        val messaggio = { i: Int -> "REC-%04d %s".format(i, "x".repeat(40)) }
        repeat(40) { i -> logger.info(messaggio(i)) }
        handler.flush()

        val file0 = cartella.resolve("snastro.0.log")
        val file1 = cartella.resolve("snastro.1.log")
        val file2 = cartella.resolve("snastro.2.log")
        assertTrue(Files.exists(file0), "snastro.0.log (il piu' recente) deve esistere")
        assertTrue(Files.exists(file1), "snastro.1.log deve esistere")
        assertTrue(Files.exists(file2), "snastro.2.log (il piu' vecchio dei 3) deve esistere")
        assertFalse(Files.exists(cartella.resolve("snastro.3.log")), "mai un quarto file: numeroFile = 3")

        // Ogni record ha la STESSA lunghezza formattata (stesso punto di chiamata, stesso testo a parte l'indice
        // a 4 cifre): la si ricava dal contenuto stesso di snastro.1.log, gia' chiuso da una rotazione, invece
        // di ricostruire a mano l'header di SimpleFormatter (dipende dal chiamante inferito da JUL).
        val contenutoFile1 = Files.readString(file1)
        val recordInFile1 = contenutoFile1.split("REC-").size - 1
        assertTrue(recordInFile1 > 0, "snastro.1.log deve contenere almeno un record")
        val unRecord = Files.size(file1) / recordInFile1

        listOf(file0, file1, file2).forEach { file ->
            assertTrue(
                Files.size(file) <= limite + unRecord,
                "$file (${Files.size(file)} byte) supera il limite ($limite) piu' un record ($unRecord)",
            )
        }

        val contenuto = contenutoFile1 + Files.readString(file0) + Files.readString(file2)
        assertTrue("REC-0039" in contenuto, "l'ultimo record scritto e' ancora presente in uno dei 3 file")
        assertFalse("REC-0000" in contenuto, "il contenuto piu' vecchio e' sparito dopo le rotazioni successive")
    }

    @Test
    fun `AC-C89 un report attraverso la Segnalazione appare nel file con messaggio e stack trace, dopo il flush`(
        @TempDir cartella: Path,
    ) {
        val handler = checkNotNull(configuraLoggingApp(cartellaLog = cartella))

        segnalazioneApp.segnala("lavoro di RitentaConBackoff fallito", IllegalStateException("causa di prova"))
        handler.flush()

        val contenuto = Files.readString(cartella.resolve("snastro.0.log"))
        assertTrue("lavoro di RitentaConBackoff fallito" in contenuto, "il messaggio e' nel file")
        assertTrue("IllegalStateException" in contenuto, "lo stack trace della causa e' nel file")
        assertTrue("causa di prova" in contenuto, "il messaggio della causa e' nel file")
    }

    @Test
    fun `AC-C90 una cartella di log non creabile ricade sulla console con un warning, mai un avvio fallito`(
        @TempDir cartella: Path,
    ) {
        // Un file REGOLARE al posto della cartella genitore: Files#createDirectories lancia in modo
        // portabile (mai dipendente dai permessi POSIX, che un utente root ignorerebbe).
        val bloccata = cartella.resolve("non-una-cartella")
        Files.write(bloccata, byteArrayOf(0))

        val catturati = mutableListOf<LogRecord>()
        val spia = object : Handler() {
            override fun publish(record: LogRecord) {
                catturati += record
            }

            override fun flush() = Unit
            override fun close() = Unit
        }
        logger.addHandler(spia)

        val handler = configuraLoggingApp(cartellaLog = bloccata.resolve("log")) // non lancia mai (AC-C90)

        assertNull(handler, "nessun handler di file installato quando la cartella non e' creabile")
        assertEquals(1, catturati.size, "un solo warning, mai un avvio fallito")
        assertEquals(Level.WARNING, catturati.single().level)
    }

    @Test
    fun `AC-C90 ogni test inietta la propria cartella temporanea, mai la cartella reale dell utente`() {
        val cartellaUtenteFinta = "/tmp/utente-di-prova-mai-reale"
        val log = CartellaLogApp.risolvi(sistemaOperativo = "Mac OS X", cartellaUtente = cartellaUtenteFinta)

        assertTrue(log.toString().startsWith(cartellaUtenteFinta), "la cartella segue SEMPRE cartellaUtente iniettato")
        assertTrue(
            System.getProperty("user.home").orEmpty() !in log.toString(),
            "mai la cartella reale dell'utente che esegue il test",
        )
    }

    /**
     * Rework cycle 1, #3: AC-C90 richiede esplicitamente "asserted by pointing user.home at a temp dir" — i
     * test sopra passano sempre `cartellaLog` esplicito, non provano mai il ramo di DEFAULT
     * (`configuraLoggingApp()`, che usa [cartellaLogAppReale] e quindi il VERO `user.home`). Qui si punta
     * `user.home` a una cartella finta e si chiama [configuraLoggingApp] SENZA argomento: la scrittura deve
     * seguire la cartella finta, e la cartella reale dell'utente non deve guadagnare alcun file nuovo.
     */
    @Test
    fun `AC-C90 con user_home ridiretto a una cartella finta configuraLoggingApp scrive li, mai nella cartella reale`(
        @TempDir cartellaUtenteFinta: Path,
    ) {
        val cartellaLogReale = cartellaLogAppReale()
        val contenutoRealePrima = elencoFile(cartellaLogReale)
        val userHomeOriginale = System.getProperty("user.home")
        try {
            System.setProperty("user.home", cartellaUtenteFinta.toString())
            val cartellaLogFinta = cartellaLogAppReale()
            assertTrue(
                cartellaLogFinta.startsWith(cartellaUtenteFinta),
                "cartellaLogAppReale deve seguire lo user.home CORRENTE, mai uno risolto in anticipo",
            )

            val handler = checkNotNull(configuraLoggingApp()) // il ramo di DEFAULT: cartellaLog = cartellaLogAppReale()
            segnalazioneApp.segnala("mai nella cartella reale dell'utente", null)
            handler.flush()

            assertTrue(Files.exists(cartellaLogFinta), "la cartella FINTA (sotto user.home ridiretto) ha il log")
            assertEquals(
                contenutoRealePrima,
                elencoFile(cartellaLogReale),
                "la cartella REALE dell'utente non deve guadagnare alcun file nuovo",
            )
        } finally {
            System.setProperty("user.home", userHomeOriginale)
        }
    }

    private fun elencoFile(cartella: Path): Set<String> =
        if (Files.isDirectory(cartella)) {
            Files.list(cartella).use { it.map(Path::toString).toList() }.toSet()
        } else {
            emptySet()
        }
}
