package snastro.progetto.adattatori.porte

import snastro.kernel.ProgettoId
import snastro.progetto.applicazione.porte.VoceRegistro
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * AC-328: the SEPARATE-JVM writer spawned by `RegistroProgettiFileRobustezzaTest` — a second app
 * instance on the same per-user registry. Args: `<file> <pronto> <via> <n>`. It builds its own
 * [RegistroProgettiFile], signals "ready" by creating `<pronto>`, waits for the test's `<via>`
 * start marker (so both processes write in parallel), then registers `n` distinct entries
 * `/figlio/progetto-<i>.snastro`. Exit code 0 only if every write returned normally.
 */
object ScrittoreRegistroFiglio {
    private const val ATTESA_MASSIMA_SECONDI = 60L
    private const val PAUSA_MILLIS = 2L
    private const val USCITA_TIMEOUT = 2

    @JvmStatic
    fun main(args: Array<String>) {
        val (file, pronto, via) = args.take(3).map { Path.of(it) }
        val quante = args[3].toInt()
        val registro = RegistroProgettiFile(file)
        Files.createFile(pronto)
        val scadenza = System.nanoTime() + TimeUnit.SECONDS.toNanos(ATTESA_MASSIMA_SECONDI)
        while (!Files.exists(via)) {
            if (System.nanoTime() > scadenza) kotlin.system.exitProcess(USCITA_TIMEOUT)
            Thread.sleep(PAUSA_MILLIS)
        }
        (0 until quante).forEach { i ->
            registro.registra(
                VoceRegistro(
                    progettoId = ProgettoId("figlio-$i"),
                    nome = "Figlio $i",
                    percorso = "/figlio/progetto-$i.snastro",
                    numRegistrazioni = 1,
                    ultimaAttivita = Instant.parse("2026-09-23T10:15:30Z"),
                ),
            )
        }
    }
}
