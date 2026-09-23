package snastro.modelli

import snastro.kernel.Esito
import snastro.kernel.mappa
import snastro.kernel.poi
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Copies [flusso] into [uscita], capped at [totale] bytes (never more), throttling [progresso] to
 * at most one call per 1% or [INTERVALLO_PROGRESSO_NANOS] (except the last, always reported) — and
 * closes [flusso] the moment [timeoutInattivita] passes without a byte read (AC-338), so a stalled
 * server never blocks [copia] forever.
 */
internal class CopiaConTimeout(
    private val id: String,
    private val totale: Long,
    private val timeoutInattivita: Duration,
    private val progresso: (id: String, scaricati: Long, totali: Long) -> Unit,
) {
    private var ultimaPercentualeAvvisata = -1L
    private var ultimoAvvisoNanos = 0L

    fun copia(flusso: InputStream, uscita: OutputStream, scrittiIniziali: Long): Esito<Long> {
        val ultimaAttivitaNanos = AtomicLong(System.nanoTime())
        val watchdog = avviaControlloInattivita(flusso, ultimaAttivitaNanos)
        return try {
            eseguiCopia(flusso, uscita, scrittiIniziali, ultimaAttivitaNanos)
        } finally {
            watchdog.shutdownNow()
        }
    }

    private fun eseguiCopia(
        flusso: InputStream,
        uscita: OutputStream,
        scrittiIniziali: Long,
        ultimaAttivitaNanos: AtomicLong,
    ): Esito<Long> {
        var scritti = scrittiIniziali
        val buffer = ByteArray(DIMENSIONE_BUFFER)
        while (scritti < totale) {
            val passo = leggiScrivi(flusso, uscita, buffer, totale - scritti, ultimaAttivitaNanos)
            val letti = when (passo) {
                is Esito.Errore -> return passo
                is Esito.Ok -> passo.valore
            }
            if (letti == -1) break
            scritti += letti
            avvisaProgresso(scritti)
        }
        return Esito.Ok(scritti)
    }

    /** Reads one chunk (capped at [max]) and writes it; `Ok(-1)` = end of stream. */
    private fun leggiScrivi(
        flusso: InputStream,
        uscita: OutputStream,
        buffer: ByteArray,
        max: Long,
        ultimaAttivitaNanos: AtomicLong,
    ): Esito<Int> = leggi(flusso, buffer, max).poi { letti ->
        if (letti == -1) {
            Esito.Ok(-1)
        } else {
            ultimaAttivitaNanos.set(System.nanoTime())
            scrivi(uscita, buffer, letti).mappa { letti }
        }
    }

    private fun leggi(flusso: InputStream, buffer: ByteArray, max: Long): Esito<Int> = try {
        Esito.Ok(flusso.read(buffer, 0, minOf(buffer.size.toLong(), max).toInt()))
    } catch (e: IOException) {
        Esito.Errore(ErroreModelli.DownloadFallito("'$id': ${e.message ?: e.javaClass.simpleName}"))
    }

    private fun scrivi(uscita: OutputStream, buffer: ByteArray, letti: Int): Esito<Unit> = try {
        uscita.write(buffer, 0, letti)
        Esito.Ok(Unit)
    } catch (e: IOException) {
        Esito.Errore(ErroreModelli.ScritturaFallita(e.message ?: e.javaClass.simpleName))
    }

    private fun avviaControlloInattivita(
        flusso: InputStream,
        ultimaAttivitaNanos: AtomicLong,
    ): ScheduledExecutorService {
        val esecutore = Executors.newSingleThreadScheduledExecutor { azione ->
            Thread(azione, "modelli-inattivita-$id").apply { isDaemon = true }
        }
        val timeoutNanos = timeoutInattivita.toNanos()
        val periodo = maxOf(timeoutNanos / DIVISORE_PERIODO_CONTROLLO, PERIODO_MINIMO_NANOS)
        esecutore.scheduleWithFixedDelay({
            if (System.nanoTime() - ultimaAttivitaNanos.get() >= timeoutNanos) chiudiInSilenzio(flusso)
        }, timeoutNanos, periodo, TimeUnit.NANOSECONDS)
        return esecutore
    }

    private fun chiudiInSilenzio(flusso: InputStream) {
        try {
            flusso.close()
        } catch (ignore: IOException) {
            // Best-effort: this stream is already being discarded, a failed close changes nothing observable.
        }
    }

    private fun avvisaProgresso(scritti: Long) {
        val percentuale = if (totale > 0) scritti * PERCENTO / totale else PERCENTO
        val ora = System.nanoTime()
        val ultimo = scritti == totale
        val scaduto = ora - ultimoAvvisoNanos >= INTERVALLO_PROGRESSO_NANOS
        if (ultimo || percentuale != ultimaPercentualeAvvisata || scaduto) {
            progresso(id, scritti, totale)
            ultimaPercentualeAvvisata = percentuale
            ultimoAvvisoNanos = ora
        }
    }

    private companion object {
        const val DIMENSIONE_BUFFER = 64 * 1024
        const val PERCENTO = 100L
        const val DIVISORE_PERIODO_CONTROLLO = 4L
        const val PERIODO_MINIMO_NANOS = 1_000_000L // 1 ms
        val INTERVALLO_PROGRESSO_NANOS: Long = Duration.ofMillis(100).toNanos()
    }
}
