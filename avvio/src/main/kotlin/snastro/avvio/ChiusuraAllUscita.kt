package snastro.avvio

import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** fix-batch-16 LOW-3: the longest the app's exit ever waits for the open project to close. */
internal const val ATTESA_CHIUSURA_USCITA_MS = 3_000L

/**
 * fix-batch-16 LOW-3: closing the window first closes the open project ([chiudi], i.e.
 * `SessioneProgetto.chiudi`), so a running Elaborazione is stopped and the database closed cleanly
 * where possible — bounded: [chiudi] runs on its own daemon thread and the exit waits for it at most
 * [attesaMassimaMs], never a hung exit (a project not closed in time is recovered at its next open,
 * `RecuperaElaborazioniInterrotte`). Returns `true` if [chiudi] finished in time.
 */
internal fun chiudiPrimaDiUscire(chiudi: () -> Unit, attesaMassimaMs: Long = ATTESA_CHIUSURA_USCITA_MS): Boolean {
    val chiusura = thread(isDaemon = true, name = "chiusura-progetto-uscita") { chiudi() }
    chiusura.join(attesaMassimaMs)
    return !chiusura.isAlive
}

/**
 * L530e: a bounded drain of [esecutore]'s own queue — `shutdown()` (no new task accepted from this
 * point on) then `awaitTermination` for at most [attesaMassimaMs]. A single-thread DAEMON executor
 * (like [SessioneProgettoImpl]'s own registry writer, [attendiScritturaRegistro]) is simply KILLED at
 * JVM shutdown otherwise, with no guarantee its last queued write ever ran. Bounded like
 * [chiudiPrimaDiUscire]: never a hung exit either. Returns `true` if the queue drained in time.
 */
internal fun spegniEAttendi(esecutore: ExecutorService, attesaMassimaMs: Long = ATTESA_CHIUSURA_USCITA_MS): Boolean {
    esecutore.shutdown()
    return esecutore.awaitTermination(attesaMassimaMs, TimeUnit.MILLISECONDS)
}
