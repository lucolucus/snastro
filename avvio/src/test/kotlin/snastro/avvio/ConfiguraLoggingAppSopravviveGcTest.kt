package snastro.avvio

import org.junit.jupiter.api.io.TempDir
import java.lang.ref.WeakReference
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Rework cycle 1, HIGH #1: before the fix, [configuraLoggingApp] attached the rotating [java.util.logging.FileHandler]
 * to a LOCAL `Logger.getLogger("snastro")`, and [segnalazioneApp] re-fetched the same name later — with nothing
 * else holding it, JUL's own weak-reference map let a garbage collection between the two calls collect both the
 * logger and its handler, and the report then silently never reached the file. The fix is [loggerSnastro], a
 * strong top-level reference both go through.
 *
 * This class deliberately holds NO field referencing the `"snastro"` logger — unlike [ConfiguraLoggingAppTest]'s
 * own `logger` field, which (kept alive by the running test instance for the whole test) would itself mask the
 * bug: only [loggerSnastro] may keep the logger reachable across the forced GC below. Without the fix this test
 * is RED (the report lands on the console, never in the file); with it, GREEN.
 */
class ConfiguraLoggingAppSopravviveGcTest {
    @Test
    fun `HIGH-1 un report sopravvive a una gc forzata tra la configurazione e il report`(@TempDir cartella: Path) {
        val handler = checkNotNull(configuraLoggingApp(cartellaLog = cartella))
        try {
            // La stessa sonda del verificatore: nessun riferimento forte locale al Logger "snastro" e' tenuto
            // qui (solo la WeakReference sotto, per SONDARE se e' stato raccolto — mai per tenerlo in vita),
            // cosi' solo l'aggancio prodotto da configuraLoggingApp (via loggerSnastro) puo' farlo sopravvivere.
            val sonda = WeakReference(Logger.getLogger("snastro"))
            repeat(NUMERO_GC_FORZATE) {
                repeat(PRESSIONE_ALLOCAZIONE) { ByteArray(SIZE_ALLOCAZIONE) } // aiuta il GC a raccogliere davvero
                System.gc()
                if (sonda.get() == null) return@repeat
            }

            segnalazioneApp.segnala("report dopo una gc forzata", IllegalStateException("causa di prova"))
            handler.flush()

            val contenuto = Files.readString(cartella.resolve("snastro.0.log"))
            assertTrue(
                "report dopo una gc forzata" in contenuto,
                "il file handler deve sopravvivere alla gc: il logger 'snastro' e' tenuto da un riferimento forte",
            )
        } finally {
            loggerSnastro.removeHandler(handler)
            handler.close()
        }
    }

    private companion object {
        const val NUMERO_GC_FORZATE = 20
        const val PRESSIONE_ALLOCAZIONE = 50
        const val SIZE_ALLOCAZIONE = 1_000_000
    }
}
